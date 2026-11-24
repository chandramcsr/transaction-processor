package com.highnote.processor.parser;

import com.highnote.processor.model.Transaction;
import com.highnote.processor.model.TransactionType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses lines of the form {@code TYPE,CARD_ID,TXN_ID,AMOUNT}.
 * <p>
 * Hand-written single-pass scanner, because this is the hot path for large files.
 * Profiling showed {@code String.split} and an amount regex were about 30% of total CPU.
 * The only allocations per line are the three field strings, the BigDecimal and the Transaction.
 * <p>
 * Stateless, so one instance is safe to share across threads.
 */
public final class TransactionParser {

    private static final char DELIMITER = ',';
    private static final int FIELD_COUNT = 4;
    private static final char BYTE_ORDER_MARK = '\uFEFF';
    private static final int MAX_INTEGER_DIGITS = 15;
    private static final int MAX_FRACTION_DIGITS = 2;
    private static final Map<String, TransactionType> TYPES_BY_NAME = new HashMap<>();

    static {
        for (TransactionType type : TransactionType.values()) {
            TYPES_BY_NAME.put(type.name(), type);
        }
    }

    public Transaction parse(String line) {
        if (line == null || line.isBlank()) {
            throw new InvalidTransactionException("Blank line");
        }
        int start = line.charAt(0) == BYTE_ORDER_MARK ? 1 : 0;   // UTF-8 BOM from some Windows editors

        int comma1 = line.indexOf(DELIMITER, start);
        int comma2 = comma1 < 0 ? -1 : line.indexOf(DELIMITER, comma1 + 1);
        int comma3 = comma2 < 0 ? -1 : line.indexOf(DELIMITER, comma2 + 1);
        if (comma3 < 0 || line.indexOf(DELIMITER, comma3 + 1) >= 0) {
            throw new InvalidTransactionException("Expected " + FIELD_COUNT + " fields but got "
                    + line.split(String.valueOf(DELIMITER), -1).length + ": " + line);
        }

        TransactionType type = parseType(field(line, start, comma1), line);
        String cardId = requireNonBlank(field(line, comma1 + 1, comma2), "cardId", line);
        String txnId = requireNonBlank(field(line, comma2 + 1, comma3), "txnId", line);
        BigDecimal amount = parseAmount(line, comma3 + 1, line.length());

        return new Transaction(type, cardId, txnId, amount);
    }

    /** Substring between from and to, trimmed with the same rule as {@link String#trim()}. */
    private static String field(String line, int from, int to) {
        int start = trimStart(line, from, to);
        return line.substring(start, trimEnd(line, start, to));
    }

    /** First index in [from, to) that isn't whitespace (a char {@code <= ' '}), or {@code to}. */
    private static int trimStart(String line, int from, int to) {
        while (from < to && line.charAt(from) <= ' ') {
            from++;
        }
        return from;
    }

    /** End index after dropping trailing whitespace from [from, to). */
    private static int trimEnd(String line, int from, int to) {
        while (to > from && line.charAt(to - 1) <= ' ') {
            to--;
        }
        return to;
    }

    private static TransactionType parseType(String value, String line) {
        TransactionType type = TYPES_BY_NAME.get(value);                 // fast path: already uppercase
        if (type == null) {
            type = TYPES_BY_NAME.get(value.toUpperCase(Locale.ROOT));    // slow path: "load", "Auth"
        }
        if (type == null) {
            throw new InvalidTransactionException("Unknown transaction type '" + value + "': " + line);
        }
        return type;
    }

    /**
     * Strict money format: digits, optionally '.' and 1-2 decimals. No sign, no exponent, at most
     * 15 integer digits. Validated and converted in one pass straight to an exact unscaled long;
     * no regex, no double.
     */
    private static BigDecimal parseAmount(String line, int from, int to) {
        from = trimStart(line, from, to);
        to = trimEnd(line, from, to);

        long unscaled = 0;
        int integerDigits = 0;
        int fractionDigits = 0;
        boolean seenDot = false;
        for (int i = from; i < to; i++) {
            char ch = line.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (seenDot ? ++fractionDigits > MAX_FRACTION_DIGITS : ++integerDigits > MAX_INTEGER_DIGITS) {
                    throw invalidAmount(line, from, to);
                }
                unscaled = unscaled * 10 + (ch - '0');
            } else if (ch == '.' && !seenDot) {
                seenDot = true;
            } else {
                throw invalidAmount(line, from, to);
            }
        }
        if (integerDigits == 0 || (seenDot && fractionDigits == 0)) {
            throw invalidAmount(line, from, to);
        }
        return BigDecimal.valueOf(unscaled, fractionDigits);   // exact: 30.00 -> unscaled 3000, scale 2
    }

    private static InvalidTransactionException invalidAmount(String line, int from, int to) {
        return new InvalidTransactionException("Invalid amount '" + line.substring(from, to)
                + "' (expected a non-negative number with at most 2 decimal places): " + line);
    }

    private static String requireNonBlank(String value, String field, String line) {
        if (value.isEmpty()) {
            throw new InvalidTransactionException("Missing " + field + ": " + line);
        }
        return value;
    }
}
