package com.highnote.processor.rules;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.model.DeclineReason;

import java.math.BigDecimal;
import java.util.Optional;

/** Declines when the amount is more than the card's available balance (ledger minus open holds). */
public final class SufficientFundsRule implements AuthorizationRule {

    @Override
    public Optional<DeclineReason> evaluate(CardAccount account, String txnId, BigDecimal amount) {
        return account.availableBalance().compareTo(amount) < 0
                ? Optional.of(DeclineReason.INSUFFICIENT_FUNDS)
                : Optional.empty();
    }
}
