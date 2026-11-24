package com.highnote.processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a large, valid input file for trying out {@link Main} on big inputs.
 * <pre>
 *   gradlew generateInput -Pout=big.txt -Plines=1000000
 * </pre>
 * Layout: one LOAD per card, then AUTH/CLEAR pairs spread across cards.
 */
public final class InputFileGenerator {

    private static final int CARDS = 10_000;

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "big.txt");
        int lines = args.length > 1 ? Integer.parseInt(args[1]) : 1_000_000;

        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines; i++) {
                if (i < CARDS) {
                    writer.write("LOAD,card" + i + ",l" + i + ",1000000.00");
                } else {
                    String card = "card" + (i / 2) % CARDS;
                    writer.write(i % 2 == 0
                            ? "AUTH," + card + ",a" + i + ",12.34"
                            : "CLEAR," + card + ",a" + (i - 1) + ",12.34");
                }
                writer.newLine();
            }
        }
        System.out.printf("Wrote %,d lines (%.1f MB) to %s%n",
                lines, Files.size(out) / 1e6, out.toAbsolutePath());
    }
}
