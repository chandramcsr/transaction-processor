package com.highnote.processor.rules;

import com.highnote.processor.account.CardAccount;
import com.highnote.processor.model.DeclineReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizerTest {

    private final Authorizer authorizer = Authorizer.withDefaultRules();
    private CardAccount account;

    @BeforeEach
    void setUp() {
        account = new CardAccount("card1");
        account.load(amount("100"));
    }

    @Test
    void approvalPlacesHold() {
        Optional<DeclineReason> decline = authorizer.authorize(account, "t1", amount("30"));

        assertThat(decline).isEmpty();
        assertThat(account.availableBalance()).isEqualByComparingTo("70");
    }

    @Test
    void declineDoesNotHoldFunds() {
        Optional<DeclineReason> decline = authorizer.authorize(account, "t1", amount("101"));

        assertThat(decline).contains(DeclineReason.INSUFFICIENT_FUNDS);
        assertThat(account.availableBalance()).isEqualByComparingTo("100");
    }

    @Test
    void ruleThatThrowsLeavesAccountUnchanged() {
        AuthorizationRule broken = (acct, txnId, amount) -> {
            throw new IllegalStateException("risk service down");
        };
        Authorizer withBrokenRule = new Authorizer(List.of(broken));

        assertThatThrownBy(() -> withBrokenRule.authorize(account, "t1", amount("10")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.availableBalance()).isEqualByComparingTo("100");
    }

    @Test
    void firstFailingRuleWins() {
        account.setSpendLimit(amount("10"));

        // fails both funds and limit; funds comes first in default order
        Optional<DeclineReason> decline = authorizer.authorize(account, "t1", amount("500"));

        assertThat(decline).contains(DeclineReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void acceptsCustomRules() {
        AuthorizationRule blockAll = (acct, txnId, amount) -> Optional.of(DeclineReason.SPEND_LIMIT_EXCEEDED);
        Authorizer custom = new Authorizer(List.of(blockAll));

        assertThat(custom.authorize(account, "t1", amount("1"))).contains(DeclineReason.SPEND_LIMIT_EXCEEDED);
        assertThat(account.availableBalance()).isEqualByComparingTo("100");
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
