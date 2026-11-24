package com.highnote.processor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void printsReportForGivenInput() {
        int exitCode = run("spend-limit-scenario.txt");

        assertThat(exitCode).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8).lines())
                .startsWith("card1: available=400.00 ledger=440.00");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void usesBundledInputByDefault() {
        assertThat(run()).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("card1: available=70.00");
    }

    @Test
    void missingInputGivesCleanErrorAndExitCodeOne() {
        int exitCode = run("does-not-exist.txt");

        assertThat(exitCode).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8))
                .startsWith("Error: Input not found")
                .contains("does-not-exist.txt");
    }
}
