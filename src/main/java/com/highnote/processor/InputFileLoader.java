package com.highnote.processor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Loads transaction input from a file path, or by name from the classpath (src/main/resources).
 */
public final class InputFileLoader {

    private InputFileLoader() {
    }

    /**
     * Streams lines lazily: memory stays flat whatever the file size.
     * Close the stream when done (try-with-resources). If you really need an array,
     * collect it with {@code stream.toArray(String[]::new)}.
     */
    public static Stream<String> streamLines(String location) {
        try {
            return Files.lines(resolve(location), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + location, e);
        }
    }

    /** A file relative to the working directory wins; otherwise look on the classpath. */
    private static Path resolve(String location) {
        Path file = Path.of(location);
        if (Files.isRegularFile(file)) {
            return file;
        }
        URL resource = InputFileLoader.class.getClassLoader().getResource(location);
        if (resource == null) {
            throw new IllegalArgumentException("Input not found as a file or on the classpath: " + location);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Bad resource URL: " + resource, e);
        }
    }
}
