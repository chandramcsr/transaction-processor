package com.highnote.processor;

import com.highnote.processor.parser.TransactionParser;
import com.highnote.processor.rules.AuthorizationRule;
import com.highnote.processor.rules.Authorizer;
import com.highnote.processor.rules.SufficientFundsRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Duplicate handling and failure isolation (single-threaded). */
class TransactionProcessorTest {

    private final TransactionProcessor processor = new TransactionProcessor();

    private List<String> run(String... lines) {
        for (int i = 0; i < lines.length; i++) {
            processor.process(i + 1, lines[i]);
        }
        return processor.report();
    }

    @Test
    void duplicateLoadIsAppliedOnce() {
        assertThat(run(
                "LOAD,card1,l1,100",
                "LOAD,card1,l1,100"))
                .containsExactly(
                        "card1: available=100.00 ledger=100.00",
                        "ERROR line 2: Duplicate LOAD l1 on card card1 ignored");
    }

    @Test
    void duplicateClearIsAppliedOnce() {
        assertThat(run(
                "LOAD,card1,l1,100",
                "AUTH,card1,t1,30",
                "CLEAR,card1,t1,30",
                "CLEAR,card1,t1,30"))
                .containsExactly(
                        "card1: available=70.00 ledger=70.00",
                        "ERROR line 4: No open authorization t1 on card card1");   // hold already consumed
    }

    @Test
    void retryOfDeclinedAuthIsDeclinedAsDuplicate() {
        assertThat(run(
                "LOAD,card1,l1,10",
                "AUTH,card1,t1,50",
                "LOAD,card1,l2,100",
                "AUTH,card1,t1,50"))
                .containsExactly(
                        "card1: available=110.00 ledger=110.00",
                        "DECLINED t1 card1 INSUFFICIENT_FUNDS",
                        "DECLINED t1 card1 DUPLICATE_TRANSACTION");
    }

    @Test
    void sameTxnIdOnDifferentTypesOrCardsIsNotADuplicate() {
        assertThat(run(
                "LOAD,card1,x,100",
                "LOAD,card2,x,100",
                "AUTH,card1,x,30",
                "CLEAR,card1,x,30"))
                .containsExactly(
                        "card1: available=70.00 ledger=70.00",
                        "card2: available=100.00 ledger=100.00");
    }

    @Test
    void failedClearReleasesItsClaimSoALaterRetrySucceeds() {
        // CLEAR arrives before its AUTH (out of order). It fails, but isn't marked as processed.
        assertThat(run(
                "LOAD,card1,l1,100",
                "CLEAR,card1,t1,30",
                "AUTH,card1,t1,30",
                "CLEAR,card1,t1,30"))
                .containsExactly(
                        "card1: available=70.00 ledger=70.00",
                        "ERROR line 2: No open authorization t1 on card card1");
    }

    @Test
    void unexpectedFailureIsIsolatedAndStateIsUnchanged() {
        AtomicBoolean riskServiceDown = new AtomicBoolean(true);
        AuthorizationRule flakyRiskCheck = (account, txnId, amount) -> {
            if (riskServiceDown.get()) {
                throw new IllegalStateException("risk service timeout");
            }
            return Optional.empty();
        };
        TransactionProcessor flaky = new TransactionProcessor(new TransactionParser(),
                new Authorizer(List.of(new SufficientFundsRule(), flakyRiskCheck)));

        flaky.process(1, "LOAD,card1,l1,100");
        flaky.process(2, "AUTH,card1,t1,30");   // blows up partway through
        flaky.process(3, "LOAD,card1,l2,5");    // processing continues
        riskServiceDown.set(false);
        flaky.process(4, "AUTH,card1,t1,30");   // retry of the same id is allowed: the claim was released

        assertThat(flaky.report()).containsExactly(
                "card1: available=75.00 ledger=105.00",
                "ERROR line 2: Unexpected failure processing AUTH t1 (state unchanged): "
                        + "java.lang.IllegalStateException: risk service timeout");
    }
}
