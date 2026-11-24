# transaction-processor

Practice solution for a mock Highnote-style "Transaction Processor" prompt.
Plain Java 21, Gradle 8.7 wrapper, JUnit 5 and AssertJ.

## Commands

```bash
./gradlew test                                         # all tests
./gradlew test --tests '*ConcurrencyTest*'             # one test class
./gradlew run                                          # process the bundled input.txt
./gradlew run --args="src/test/resources/spend-limit-scenario.txt"
./gradlew generateInput -Pout=big.txt -Plines=1000000  # write a large sample file
./gradlew benchmark                                    # throughput benchmark
```

## Input format

`TYPE,CARD_ID,TXN_ID,AMOUNT`, where TYPE is `LOAD | AUTH | CLEAR | REVERSE | LIMIT`.

## Assumptions (share these with the interviewer)

**Input**
- Amounts are non-negative, with at most 2 decimal places. No sign, no exponent.
- Whitespace around fields and lowercase types are accepted. Blank lines are skipped but still count toward line numbers.
- A card account is created the first time its card id appears.

**Business rules**
- `LOAD` adds funds. `AUTH` places a hold if every rule passes. `CLEAR` releases the hold and debits the cleared amount
  (which may differ from the held amount, e.g. tips). `REVERSE` releases the hold only.
- `LIMIT` sets or replaces a cumulative spend limit. Spend = cleared amounts + open holds. There's no limit until one is set.
- Rules run in order (funds, then limit); the first failure is the decline reason.

**Duplicates and errors**
- A repeated `AUTH` id on a card is declined `DUPLICATE_TRANSACTION`, even if the first one was declined.
  A repeated `LOAD` or `LIMIT` is ignored and reported.
- A repeated `CLEAR` or `REVERSE` fails because its hold is gone ("No open authorization").
- A failed message (e.g. a `CLEAR` that arrives before its `AUTH`) isn't marked as processed, so a later retry can succeed.
- A bad line never stops the run; it's reported as `ERROR line N: ...`.

**Output**
- Balances sorted by card id, then declines, then errors (each in input order). Amounts are printed with 2 decimals.

## Design

| Class | Responsibility |
|---|---|
| `Main` | CLI: reads the file, prints the report, exit code 1 if the input can't be read |
| `InputFileLoader` | Streams lines from a file path, or from the classpath |
| `TransactionProcessorApp` | Pipeline: streams lines one at a time into the processor in input order, returns the report; `processTransactions(String[])` is the scaffold entry point |
| `TransactionProcessor` | Thread-safe core: per-card lock, duplicate check, per-line error isolation, builds the report |
| `parser.TransactionParser` | Line to `Transaction`, or `InvalidTransactionException`. Stateless single-pass scanner |
| `account.CardAccount` | Balances, holds, spend limit, processed ids. O(1) queries; computes first, commits last |
| `rules.*` | One class per `AuthorizationRule`; `Authorizer` runs them and returns `Optional<DeclineReason>` |

## Performance and memory

- Single-threaded: about 1M lines/s (`./gradlew benchmark`). To scale across cores, route lines by card id to one
  worker per core (same card, same worker), which keeps each card's order while cards run in parallel.
- Input is streamed, so memory doesn't grow with line count. It grows with the number of cards and the stored
  `AUTH`/`LOAD`/`LIMIT` ids: a 10M-line (303 MB) file fits in a 512 MB heap.
- For files bigger than memory: split the input by card hash into bucket files, process one bucket at a time,
  then merge the reports. In production, the state lives in a database with a unique key on `(card_id, txn_id)`.
