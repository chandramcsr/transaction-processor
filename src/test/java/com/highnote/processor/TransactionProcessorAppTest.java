package com.highnote.processor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end tests through the scaffold entry point. Business assumptions are listed in README.md. */
class TransactionProcessorAppTest {

    private final TransactionProcessorApp app = new TransactionProcessorApp();

    @Test
    void processesBundledInputFile() {
        String[] inputFileLines = loadLines("input.txt");   // independent of working directory

        List<String> output = app.processTransactions(inputFileLines);

        assertThat(output).isNotEmpty();
    }

    @Test
    void processesMixedScenarioFile() {
        String[] lines = loadLines("mixed-scenario.txt");

        List<String> output = app.processTransactions(lines);

        assertThat(output).containsExactly(
                "card1: available=70.00 ledger=70.00",
                "card2: available=50.00 ledger=50.00",
                "DECLINED t2 card1 INSUFFICIENT_FUNDS",
                "ERROR line 8: Unknown transaction type 'FOO': FOO,card2,t4,1.00");
    }

    @Test
    void enforcesSpendLimitScenarioFile() {
        String[] lines = loadLines("spend-limit-scenario.txt");

        List<String> output = app.processTransactions(lines);

        assertThat(output).containsExactly(
                "card1: available=400.00 ledger=440.00",
                "card2: available=20.00 ledger=20.00",
                "DECLINED t2 card1 SPEND_LIMIT_EXCEEDED",
                "DECLINED t1 card1 DUPLICATE_TRANSACTION",
                "DECLINED t5 card2 INSUFFICIENT_FUNDS");
    }

    @Test
    void clearWithoutAuthIsReportedAndProcessingContinues() {
        String[] lines = {
                "LOAD,card1,l1,10",
                "CLEAR,card1,missing,5",
                "LOAD,card1,l2,5"
        };

        List<String> output = app.processTransactions(lines);

        assertThat(output).containsExactly(
                "card1: available=15.00 ledger=15.00",
                "ERROR line 2: No open authorization missing on card card1");
    }

    @Test
    void blankLinesAreIgnored() {
        List<String> output = app.processTransactions(new String[]{"", "LOAD,card1,l1,1", "   "});

        assertThat(output).containsExactly("card1: available=1.00 ledger=1.00");
    }

    @Test
    void printsInputsThenReport() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        TransactionProcessorApp printingApp =
                new TransactionProcessorApp(new PrintStream(buffer, true, StandardCharsets.UTF_8));

        printingApp.processTransactions(new String[]{"LOAD,card1,l1,1", "LOAD,card2,l2,2"});

        assertThat(buffer.toString(StandardCharsets.UTF_8).lines()).containsExactly(
                "Starting Transaction Processor:",
                "Start Inputs:",
                "LOAD,card1,l1,1",
                "LOAD,card2,l2,2",
                "End Inputs:",
                "Output:",
                "card1: available=1.00 ledger=1.00",
                "card2: available=2.00 ledger=2.00");
    }

    /** Scaffold's processTransactions takes String[], so collect the stream into an array. */
    private static String[] loadLines(String location) {
        try (Stream<String> lines = InputFileLoader.streamLines(location)) {
            return lines.toArray(String[]::new);
        }
    }
}
