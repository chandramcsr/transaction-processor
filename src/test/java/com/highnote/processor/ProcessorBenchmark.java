package com.highnote.processor;

import com.highnote.processor.parser.TransactionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Plain-Java micro benchmark (no JMH dependency). Input is generated up front so that only
 * processing is timed, and there are warm-up rounds so the JIT has compiled the hot paths.
 * <pre>
 *   gradlew benchmark
 * </pre>
 * Numbers are indicative only; for publishable numbers use JMH.
 */
public final class ProcessorBenchmark {

    private static final int LINES = 1_000_000;
    private static final int CARDS = 10_000;
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;

    public static void main(String[] args) {
        List<String> lines = workload();
        TransactionParser parser = new TransactionParser();

        System.out.printf("%,d lines, %,d cards%n", LINES, CARDS);
        bench("parse only", () -> {
            int n = 0;
            for (String line : lines) {
                n += parser.parse(line).txnId().length();
            }
            return n;
        });
        TransactionProcessorApp app = new TransactionProcessorApp();
        bench("full run", () -> app.process(lines.stream()).size());
    }

    private static void bench(String name, Supplier<Integer> task) {
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            task.get();
        }
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            long start = System.nanoTime();
            task.get();
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed);
            total += elapsed;
        }
        System.out.printf(Locale.ROOT, "%-14s best %6d ms   avg %6d ms   %5d ns/line   %,10.0f lines/s%n",
                name, best / 1_000_000, total / MEASURED_ROUNDS / 1_000_000,
                best / LINES, LINES / (best / 1e9));
    }

    /** One LOAD per card, then AUTH/CLEAR pairs spread across cards (no declines or errors). */
    static List<String> workload() {
        List<String> lines = new ArrayList<>(LINES);
        for (int i = 0; i < LINES; i++) {
            if (i < CARDS) {
                lines.add("LOAD,card" + i + ",l" + i + ",1000000.00");
            } else {
                String card = "card" + (i / 2) % CARDS;
                lines.add(i % 2 == 0
                        ? "AUTH," + card + ",a" + i + ",12.34"
                        : "CLEAR," + card + ",a" + (i - 1) + ",12.34");
            }
        }
        return lines;
    }
}
