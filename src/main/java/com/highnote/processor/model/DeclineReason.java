package com.highnote.processor.model;

/** Why an AUTH was declined. */
public enum DeclineReason {
    /** The AUTH's transaction id was already used on this card. */
    DUPLICATE_TRANSACTION,
    /** The amount is more than the available balance. */
    INSUFFICIENT_FUNDS,
    /** Cleared + held + this amount would go over the card's spend limit. */
    SPEND_LIMIT_EXCEEDED
}
