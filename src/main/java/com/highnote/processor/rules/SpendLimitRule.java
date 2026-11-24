package com.highnote.processor.rules;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.model.DeclineReason;

import java.math.BigDecimal;
import java.util.Optional;

/** Declines when cleared + held + this amount would go over the card's spend limit, if one is set. */
public final class SpendLimitRule implements AuthorizationRule {

    @Override
    public Optional<DeclineReason> evaluate(CardAccount account, String txnId, BigDecimal amount) {
        return account.spendLimit()
                .filter(limit -> account.totalSpend().add(amount).compareTo(limit) > 0)
                .map(limit -> DeclineReason.SPEND_LIMIT_EXCEEDED);
    }
}
