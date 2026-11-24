package com.highnote.processor.rules;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.model.DeclineReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRulesTest {

    private CardAccount account;

    @BeforeEach
    void setUp() {
        account = new CardAccount("card1");
        account.load(amount("100"));
    }

    @Nested
    class SufficientFunds {
        private final AuthorizationRule rule = new SufficientFundsRule();

        @Test
        void passesAtExactAvailableBalance() {
            assertThat(rule.evaluate(account, "t1", amount("100"))).isEmpty();
        }

        @Test
        void declinesOverAvailableBalance() {
            account.placeHold("t0", amount("70"));

            assertThat(rule.evaluate(account, "t1", amount("31")))
                    .contains(DeclineReason.INSUFFICIENT_FUNDS);
        }
    }

    @Nested
    class SpendLimit {
        private final AuthorizationRule rule = new SpendLimitRule();

        @Test
        void passesWhenNoLimitSet() {
            assertThat(rule.evaluate(account, "t1", amount("1000"))).isEmpty();
        }

        @Test
        void passesExactlyAtLimit() {
            account.setSpendLimit(amount("50"));
            account.placeHold("t0", amount("20"));

            assertThat(rule.evaluate(account, "t1", amount("30"))).isEmpty();
        }

        @Test
        void countsClearedAndHeldAmounts() {
            account.setSpendLimit(amount("50"));
            account.placeHold("t0", amount("20"));
            account.clear("t0", amount("20"));
            account.placeHold("t1", amount("20"));

            assertThat(rule.evaluate(account, "t2", amount("11")))
                    .contains(DeclineReason.SPEND_LIMIT_EXCEEDED);
        }

        @Test
        void reversalFreesUpSpend() {
            account.setSpendLimit(amount("50"));
            account.placeHold("t0", amount("50"));
            account.reverse("t0");

            assertThat(rule.evaluate(account, "t1", amount("50"))).isEmpty();
        }
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
