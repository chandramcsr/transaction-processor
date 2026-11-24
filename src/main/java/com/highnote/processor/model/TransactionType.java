package com.highnote.processor.model;

public enum TransactionType {
    /** Adds funds to the card balance. */
    LOAD,
    /** Places a hold on available funds if every authorization rule passes. */
    AUTH,
    /** Settles a previous AUTH: releases the hold and debits the balance. */
    CLEAR,
    /** Cancels a previous AUTH: releases the hold without debiting. */
    REVERSE,
    /** Sets (or replaces) the card's cumulative spend limit. */
    LIMIT
}
