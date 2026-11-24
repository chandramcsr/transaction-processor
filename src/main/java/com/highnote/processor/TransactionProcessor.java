package com.highnote.processor;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.account.UnknownAuthorizationException;
import com.highnote.processor.model.DeclineReason;
import com.highnote.processor.model.Transaction;
import com.highnote.processor.model.TransactionType;
import com.highnote.processor.parser.InvalidTransactionException;
import com.highnote.processor.parser.TransactionParser;
import com.highnote.processor.rules.Authorizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Applies transactions to card accounts and builds the end-of-run report.
 *
 * <h2>Thread safety</h2>
 * Safe to call from many threads at once:
 * <ul>
 *   <li>Accounts live in a {@link ConcurrentHashMap}; {@code computeIfAbsent} creates each one exactly once.</li>
 *   <li>The whole sequence (mark the transaction as processed, run the rules, change the account) runs while
 *       inside {@code CardAccount.runAtomically}, so two AUTHs on the same card can't both pass the funds check
 *       (no check-then-act race). Different cards never contend.</li>
 *   <li>Declines and errors are keyed by line number in sorted concurrent maps, so the report stays in
 *       input order however the threads interleave.</li>
 * </ul>
 * Per-card <i>ordering</i> isn't guaranteed here; that's the caller's job
 * ({@link TransactionProcessorApp} submits lines in input order from one thread).
 *
 * <h2>Failure handling</h2>
 * Every line is isolated. Invalid input, unknown authorizations, duplicates and unexpected exceptions
 * are recorded against their line number, and processing continues. If a change fails, the processed
 * mark is removed and the account is left unchanged (CardAccount commits all of its changes at the end).
 */
public final class TransactionProcessor {

    private final TransactionParser parser;
    private final Authorizer authorizer;

    private final ConcurrentMap<String, CardAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentSkipListMap<Long, String> declines = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<Long, String> errors = new ConcurrentSkipListMap<>();

    public TransactionProcessor() {
        this(new TransactionParser(), Authorizer.withDefaultRules());
    }

    public TransactionProcessor(TransactionParser parser, Authorizer authorizer) {
        this.parser = parser;
        this.authorizer = authorizer;
    }

    /** Parses and applies one raw input line. Blank lines are ignored. */
    public void process(long lineNumber, String line) {
        if (line.isBlank()) {
            return;
        }
        try {
            apply(lineNumber, parser.parse(line));
        } catch (InvalidTransactionException e) {
            recordError(lineNumber, e.getMessage());
        }
    }

    /** Applies one parsed transaction. Never throws for business or data errors. */
    public void apply(long lineNumber, Transaction txn) {
        CardAccount account = accountFor(txn.cardId());
        boolean tracked = needsIdTracking(txn.type());

        account.runAtomically(() -> {
            if (tracked && !account.markProcessed(txn.type(), txn.txnId())) {
                recordDuplicate(lineNumber, txn);
                return;
            }
            try {
                applyTo(account, lineNumber, txn);
            } catch (RuntimeException e) {
                if (tracked) {
                    account.unmarkProcessed(txn.type(), txn.txnId());   // let a corrected retry through
                }
                recordError(lineNumber, describeFailure(txn, e));
            }
        });
    }

    /** Expected business failures keep their own message; anything else is flagged as unexpected. */
    private static String describeFailure(Transaction txn, RuntimeException e) {
        if (e instanceof UnknownAuthorizationException) {
            return e.getMessage();
        }
        return "Unexpected failure processing " + txn.type() + " " + txn.txnId() + " (state unchanged): " + e;
    }

    /**
     * CLEAR and REVERSE consume their hold, so a repeat already fails with "no open authorization".
     * Storing their ids too would only cost memory (about half of all stored ids in a typical file).
     */
    private static boolean needsIdTracking(TransactionType type) {
        return type != TransactionType.CLEAR && type != TransactionType.REVERSE;
    }

    /** Lock-free {@code get} for the common case (existing card); {@code computeIfAbsent} only for a new card. */
    private CardAccount accountFor(String cardId) {
        CardAccount account = accounts.get(cardId);
        return account != null ? account : accounts.computeIfAbsent(cardId, CardAccount::new);
    }

    private void recordError(long lineNumber, String message) {
        errors.put(lineNumber, "ERROR line " + lineNumber + ": " + message);
    }

    private void applyTo(CardAccount account, long lineNumber, Transaction txn) {
        switch (txn.type()) {
            case LOAD -> account.load(txn.amount());
            case LIMIT -> account.setSpendLimit(txn.amount());
            case AUTH -> authorizer.authorize(account, txn.txnId(), txn.amount())
                    .ifPresent(reason -> recordDecline(lineNumber, txn, reason));
            case CLEAR -> account.clear(txn.txnId(), txn.amount());
            case REVERSE -> account.reverse(txn.txnId());
        }
    }

    /** A repeated AUTH is a business decline; any other repeated message is ignored and reported. */
    private void recordDuplicate(long lineNumber, Transaction txn) {
        if (txn.type() == TransactionType.AUTH) {
            recordDecline(lineNumber, txn, DeclineReason.DUPLICATE_TRANSACTION);
        } else {
            recordError(lineNumber, "Duplicate " + txn.type() + " " + txn.txnId()
                    + " on card " + txn.cardId() + " ignored");
        }
    }

    private void recordDecline(long lineNumber, Transaction txn, DeclineReason reason) {
        declines.put(lineNumber, "DECLINED " + txn.txnId() + " " + txn.cardId() + " " + reason);
    }

    /**
     * Card balances sorted by card id, then declines, then errors (each in input order).
     * Call once processing has finished.
     */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        accounts.values().stream()
                .sorted(Comparator.comparing(CardAccount::cardId))
                .map(TransactionProcessor::formatBalance)
                .forEach(lines::add);
        lines.addAll(declines.values());
        lines.addAll(errors.values());
        return lines;
    }

    private static String formatBalance(CardAccount account) {
        return account.readAtomically(() -> account.cardId() + ": available=" + format(account.availableBalance())
                + " ledger=" + format(account.ledgerBalance()));
    }

    private static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
