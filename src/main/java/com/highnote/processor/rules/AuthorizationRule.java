package com.highnote.processor.rules;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.model.DeclineReason;

import java.math.BigDecimal;
import java.util.Optional;

/** One authorization check. Returns a decline reason, or empty if the check passes. */
@FunctionalInterface
public interface AuthorizationRule {

    Optional<DeclineReason> evaluate(CardAccount account, String txnId, BigDecimal amount);
}
