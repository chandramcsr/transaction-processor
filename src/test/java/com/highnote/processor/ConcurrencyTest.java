package com.highnote.processor;

import com.highnote.processor.model.Transaction;
import com.highnote.processor.model.TransactionType;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/** TransactionProcessor stays correct when called from many threads at once (per-card locking). */
class ConcurrencyTest {

    private static final int THREADS = 32;

    /** The same transaction delivered many times at once must be applied exactly once. */
    @RepeatedTest(20)
    void sameAuthRacingFromManyThreadsIsHeldOnce() throws Exception {
        TransactionProcessor processor = new TransactionProcessor();
        processor.apply(0, txn(TransactionType.LOAD, "card1", "l1", "100"));

        runConcurrently(THREADS, i -> () ->
                processor.apply(i + 1, txn(TransactionType.AUTH, "card1", "t1", "30")));

        List<String> report = processor.report();
        assertThat(report.get(0)).isEqualTo("card1: available=70.00 ledger=100.00");
        assertThat(report).filteredOn(l -> l.contains("DUPLICATE_TRANSACTION")).hasSize(THREADS - 1);
    }

    /** Concurrent AUTHs on one card can never overdraw it (no check-then-act race). */
    @RepeatedTest(20)
    void concurrentAuthsNeverOverdraw() throws Exception {
        TransactionProcessor processor = new TransactionProcessor();
        processor.apply(0, txn(TransactionType.LOAD, "card1", "l1", "100"));

        int attempts = 300;   // 300 x 1.00 against a balance of 100
        runConcurrently(attempts, i -> () ->
                processor.apply(i + 1, txn(TransactionType.AUTH, "card1", "t" + i, "1.00")));

        List<String> report = processor.report();
        assertThat(report.get(0)).isEqualTo("card1: available=0.00 ledger=100.00");
        assertThat(report).filteredOn(l -> l.contains("INSUFFICIENT_FUNDS")).hasSize(attempts - 100);
    }

    // ---------------------------------------------------------------- helpers

    private static Transaction txn(TransactionType type, String card, String id, String amount) {
        return new Transaction(type, card, id, new BigDecimal(amount));
    }

    /** Releases all tasks at the same instant to maximize contention. */
    private static void runConcurrently(int tasks, IntFunction<Runnable> taskFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < tasks; i++) {
                Runnable task = taskFactory.apply(i);
                pool.submit(() -> {
                    start.await();
                    task.run();
                    return null;
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
