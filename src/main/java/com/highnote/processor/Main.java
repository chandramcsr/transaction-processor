package com.highnote.processor;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command-line entry point: {@code Main [input]}
 * <pre>
 *   Main              # classpath input.txt
 *   Main big.txt      # a file path (or classpath resource name)
 * </pre>
 * Exit code 0 on success, 1 if the input can't be read.
 */
public final class Main {

    private static final String DEFAULT_INPUT = "input.txt";

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Separated from {@link #main} so it can be tested without exiting the JVM. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        String location = args.length > 0 ? args[0] : DEFAULT_INPUT;

        List<String> report;
        try (Stream<String> lines = InputFileLoader.streamLines(location)) {
            report = new TransactionProcessorApp().process(lines);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
        report.forEach(out::println);
        return 0;
    }
}
