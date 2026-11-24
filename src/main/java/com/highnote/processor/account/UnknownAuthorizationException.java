package com.highnote.processor.account;

/** A CLEAR or REVERSE refers to an authorization that has no open hold on the card. */
public final class UnknownAuthorizationException extends RuntimeException {

    public UnknownAuthorizationException(String cardId, String txnId) {
        super("No open authorization " + txnId + " on card " + cardId);
    }
}
