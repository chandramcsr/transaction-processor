package com.highnote.processor.model;

import java.math.BigDecimal;
import java.util.Objects;

/** One parsed input line: {@code TYPE,CARD_ID,TXN_ID,AMOUNT}. */
public record Transaction(TransactionType type, String cardId, String txnId, BigDecimal amount) {

    public Transaction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(txnId, "txnId");
        Objects.requireNonNull(amount, "amount");
    }
}
