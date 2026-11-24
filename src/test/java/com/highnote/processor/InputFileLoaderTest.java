package com.highnote.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsFileOnDisk() throws IOException {
        Path file = Files.writeString(tempDir.resolve("in.txt"), "LOAD,c1,l1,1\nAUTH,c1,t1,1\n");

        try (Stream<String> lines = InputFileLoader.streamLines(file.toString())) {
            assertThat(lines).containsExactly("LOAD,c1,l1,1", "AUTH,c1,t1,1");
        }
    }

    @Test
    void readsClasspathResource() {
        try (Stream<String> lines = InputFileLoader.streamLines("spend-limit-scenario.txt")) {
            assertThat(lines).first().isEqualTo("LOAD,card1,l1,500");
        }
    }

    @Test
    void missingInputIsReported() {
        assertThatThrownBy(() -> InputFileLoader.streamLines("nope.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope.txt");
    }
}
