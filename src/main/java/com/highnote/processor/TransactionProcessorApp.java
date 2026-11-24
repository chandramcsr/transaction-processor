package com.highnote.processor;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs input lines through a {@link TransactionProcessor} and produces the report.
 * <p>
 * Single-threaded pipeline:
 * <ol>
 *   <li>Read: lines are streamed one at a time, so memory doesn't grow with file size.</li>
 *   <li>Process: each line is parsed and applied in input order.</li>
 *   <li>Consolidate: {@link TransactionProcessor#report()} builds balances, declines and errors.</li>
 * </ol>
 */
public final class TransactionProcessorApp {

    private final PrintStream out;

    public TransactionProcessorApp() {
        this(System.out);
    }

    public TransactionProcessorApp(PrintStream out) {
        this.out = out;
    }

    /**
     * Interview-scaffold entry point: echoes the inputs, processes them, and prints the report.
     *
     * @return report lines: balances, then declines, then errors
     */
    public List<String> processTransactions(String[] transactions) {
        printInputs(transactions);
        List<String> report = process(Arrays.stream(transactions));
        printReport(report);
        return report;
    }

    /** Processes lines without printing. Use this for large streamed files. */
    public List<String> process(Stream<String> lines) {
        TransactionProcessor processor = new TransactionProcessor();
        Iterator<String> iterator = lines.iterator();
        long lineNumber = 0;   // counts blank lines too, so errors point at the real line in the file
        while (iterator.hasNext()) {
            processor.process(++lineNumber, iterator.next());
        }
        return processor.report();
    }

    private void printInputs(String[] transactions) {
        out.println("Starting Transaction Processor:");
        out.println("Start Inputs:");
        for (String transaction : transactions) {
            out.println(transaction);
        }
        out.println("End Inputs:");
    }

    private void printReport(List<String> report) {
        out.println("Output:");
        report.forEach(out::println);
    }
}
