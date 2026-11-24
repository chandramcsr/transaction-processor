package com.highnote.processor.account;

import com.highnote.processor.model.TransactionType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Balance state for a single card. Holds no authorization policy (see the rules package).
 * <ul>
 *   <li>ledger balance = funds loaded minus cleared amounts</li>
 *   <li>available balance = ledger balance minus open holds</li>
 *   <li>total spend = cleared amounts plus open holds</li>
 * </ul>
 * Running totals keep every query O(1).
 * <p>
 * <b>Thread safety:</b> the individual methods are not synchronized. Wrap any read-decide-write
 * sequence in {@link #runAtomically} (and multi-field reads in {@link #readAtomically}).
 * The lock is a private object, so outside code can't hold or interfere with it.
 * <p>
 * <b>Failure safety:</b> each change validates and computes new values first, then commits
 * them at the end, so a failure partway through leaves the account unchanged.
 */
public final class CardAccount {

    private final String cardId;
    private BigDecimal ledgerBalance = BigDecimal.ZERO;
    private BigDecimal heldTotal = BigDecimal.ZERO;
    private BigDecimal clearedTotal = BigDecimal.ZERO;
    private BigDecimal spendLimit;   // null = no limit
    private final Map<String, BigDecimal> openHolds = new HashMap<>();
    private final EnumMap<TransactionType, Set<String>> processedIds = new EnumMap<>(TransactionType.class);

    private final Object lock = new Object();

    public CardAccount(String cardId) {
        this.cardId = cardId;
    }

    /** Runs a read-decide-write sequence (e.g. check funds, then place a hold) as one atomic step. */
    public void runAtomically(Runnable action) {
        synchronized (lock) {
            action.run();
        }
    }

    /** Reads several fields as one consistent snapshot. */
    public <T> T readAtomically(Supplier<T> read) {
        synchronized (lock) {
            return read.get();
        }
    }

    /**
     * Idempotency check, scoped to this card. Keeping the processed ids on the account
     * (instead of in one global concurrent set) means no shared structure across cards,
     * no key-string building, and only small per-card tables to resize. Called inside
     * {@link #runAtomically}, so a plain HashSet is enough.
     *
     * @return true if this (type, txnId) is new for this card and is now marked as processed
     */
    public boolean markProcessed(TransactionType type, String txnId) {
        return processedIds.computeIfAbsent(type, t -> new HashSet<>()).add(txnId);
    }

    /** Undoes {@link #markProcessed} when processing failed, so a corrected retry can go through. */
    public void unmarkProcessed(TransactionType type, String txnId) {
        Set<String> ids = processedIds.get(type);
        if (ids != null) {
            ids.remove(txnId);
        }
    }

    public String cardId() {
        return cardId;
    }

    public BigDecimal ledgerBalance() {
        return ledgerBalance;
    }

    public BigDecimal availableBalance() {
        return ledgerBalance.subtract(heldTotal);
    }

    public BigDecimal totalSpend() {
        return clearedTotal.add(heldTotal);
    }

    public Optional<BigDecimal> spendLimit() {
        return Optional.ofNullable(spendLimit);
    }

    public void load(BigDecimal amount) {
        ledgerBalance = ledgerBalance.add(amount);
    }

    public void setSpendLimit(BigDecimal limit) {
        this.spendLimit = limit;
    }

    /** Records an approved authorization. */
    public void placeHold(String txnId, BigDecimal amount) {
        if (openHolds.containsKey(txnId)) {
            throw new IllegalStateException("Hold " + txnId + " already exists on card " + cardId);
        }
        BigDecimal newHeldTotal = heldTotal.add(amount);
        // commit
        openHolds.put(txnId, amount);
        heldTotal = newHeldTotal;
    }

    /**
     * Settles an open authorization. The cleared amount may differ from the held amount
     * (e.g. tips); the full hold is released and the cleared amount is debited.
     */
    public void clear(String txnId, BigDecimal clearedAmount) {
        BigDecimal held = requireOpenHold(txnId);
        BigDecimal newHeldTotal = heldTotal.subtract(held);
        BigDecimal newLedgerBalance = ledgerBalance.subtract(clearedAmount);
        BigDecimal newClearedTotal = clearedTotal.add(clearedAmount);
        // commit
        openHolds.remove(txnId);
        heldTotal = newHeldTotal;
        ledgerBalance = newLedgerBalance;
        clearedTotal = newClearedTotal;
    }

    public void reverse(String txnId) {
        BigDecimal held = requireOpenHold(txnId);
        BigDecimal newHeldTotal = heldTotal.subtract(held);
        // commit
        openHolds.remove(txnId);
        heldTotal = newHeldTotal;
    }

    private BigDecimal requireOpenHold(String txnId) {
        BigDecimal held = openHolds.get(txnId);
        if (held == null) {
            throw new UnknownAuthorizationException(cardId, txnId);
        }
        return held;
    }
}
