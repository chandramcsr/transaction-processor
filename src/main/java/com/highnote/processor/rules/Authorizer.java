package com.highnote.processor.rules;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.model.DeclineReason;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Runs the rules in order. The first rule that fails decides the decline reason;
 * if every rule passes, a hold is placed.
 * <p>
 * Rules only read state; the only change is the final {@code placeHold}. A rule that
 * throws therefore leaves the account untouched. Duplicate detection happens earlier,
 * in {@code TransactionProcessor}.
 * <p>
 * Call it inside {@code CardAccount.runAtomically}, because evaluating the rules and placing
 * the hold has to happen as one atomic step.
 */
public final class Authorizer {

    private final List<AuthorizationRule> rules;

    public Authorizer(List<AuthorizationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Order matters: a funds problem is reported before a limit problem. */
    public static Authorizer withDefaultRules() {
        return new Authorizer(List.of(
                new SufficientFundsRule(),
                new SpendLimitRule()));
    }

    /**
     * @return the decline reason, or empty if approved (in which case a hold has been placed)
     */
    public Optional<DeclineReason> authorize(CardAccount account, String txnId, BigDecimal amount) {
        for (AuthorizationRule rule : rules) {
            Optional<DeclineReason> decline = rule.evaluate(account, txnId, amount);
            if (decline.isPresent()) {
                return decline;
            }
        }
        account.placeHold(txnId, amount);
        return Optional.empty();
    }
}
