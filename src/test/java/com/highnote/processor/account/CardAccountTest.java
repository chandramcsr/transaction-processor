package com.highnote.processor.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardAccountTest {

    private CardAccount account;

    @BeforeEach
    void setUp() {
        account = new CardAccount("card1");
        account.load(amount("100"));
    }

    @Test
    void holdReducesAvailableButNotLedger() {
        account.placeHold("t1", amount("30"));

        assertThat(account.availableBalance()).isEqualByComparingTo("70");
        assertThat(account.ledgerBalance()).isEqualByComparingTo("100");
        assertThat(account.totalSpend()).isEqualByComparingTo("30");
    }

    @Test
    void clearReleasesHoldAndDebitsClearedAmount() {
        account.placeHold("t1", amount("30"));

        account.clear("t1", amount("35"));   // e.g. tip added

        assertThat(account.ledgerBalance()).isEqualByComparingTo("65");
        assertThat(account.availableBalance()).isEqualByComparingTo("65");
        assertThat(account.totalSpend()).isEqualByComparingTo("35");
    }

    @Test
    void reverseReleasesHoldOnly() {
        account.placeHold("t1", amount("30"));

        account.reverse("t1");

        assertThat(account.ledgerBalance()).isEqualByComparingTo("100");
        assertThat(account.availableBalance()).isEqualByComparingTo("100");
        assertThat(account.totalSpend()).isEqualByComparingTo("0");
    }

    @Test
    void cannotClearOrReverseTwice() {
        account.placeHold("t1", amount("30"));
        account.reverse("t1");

        assertThatThrownBy(() -> account.reverse("t1"))
                .isInstanceOf(UnknownAuthorizationException.class);
        assertThatThrownBy(() -> account.clear("t1", amount("30")))
                .isInstanceOf(UnknownAuthorizationException.class);
    }

    @Test
    void failedOperationsLeaveStateUnchanged() {
        account.placeHold("t1", amount("30"));

        assertThatThrownBy(() -> account.placeHold("t1", amount("50")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> account.clear("missing", amount("10")))
                .isInstanceOf(UnknownAuthorizationException.class);

        assertThat(account.availableBalance()).isEqualByComparingTo("70");
        assertThat(account.ledgerBalance()).isEqualByComparingTo("100");
        assertThat(account.totalSpend()).isEqualByComparingTo("30");
    }

    @Test
    void spendLimitIsUnsetByDefaultAndCanBeReplaced() {
        assertThat(account.spendLimit()).isEmpty();

        account.setSpendLimit(amount("50"));
        account.setSpendLimit(amount("80"));

        assertThat(account.spendLimit()).contains(amount("80"));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
