package com.highnote.processor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * One million transactions, generated lazily so the input is never held in memory.
 * Memory use is bounded by the number of cards (1,000) and open holds, not by the line count.
 */
@Tag("slow")
class LargeInputTest {

    private static final int LINES = 1_000_000;
    private static final int CARDS = 1_000;
    private static final long INITIAL_BALANCE = 1_000_000;

    @Test
    void processesOneMillionLines() {
        TransactionProcessorApp app = new TransactionProcessorApp();

        long start = System.nanoTime();
        List<String> report = assertTimeoutPreemptively(Duration.ofSeconds(60), () ->
                app.process(lines()));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Processed %,d lines in %,d ms%n", LINES, millis);

        assertThat(report).hasSize(CARDS);   // no declines or errors expected
        long authCount = IntStream.range(CARDS, LINES).filter(i -> i % 2 == 0).count();
        BigDecimal expectedTotalLedger = BigDecimal.valueOf(CARDS * INITIAL_BALANCE - authCount);
        assertThat(totalLedger(report)).isEqualByComparingTo(expectedTotalLedger);
    }

    /** First, one LOAD per card. Then AUTH/CLEAR pairs (each pair on the same card). */
    private static Stream<String> lines() {
        return IntStream.range(0, LINES).mapToObj(i -> {
            if (i < CARDS) {
                return "LOAD,card" + i + ",l" + i + "," + INITIAL_BALANCE;
            }
            String card = "card" + (i / 2) % CARDS;
            return i % 2 == 0
                    ? "AUTH," + card + ",a" + i + ",1.00"
                    : "CLEAR," + card + ",a" + (i - 1) + ",1.00";
        });
    }

    private static BigDecimal totalLedger(List<String> report) {
        return report.stream()
                .map(line -> line.substring(line.indexOf("ledger=") + "ledger=".length()))
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
