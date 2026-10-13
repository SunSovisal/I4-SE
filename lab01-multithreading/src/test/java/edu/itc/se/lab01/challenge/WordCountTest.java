package edu.itc.se.lab01.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class WordCountTest {
    @TempDir
    Path dir;

    @Test
    @Timeout(10)
    void parallelMatchesSequentialOnTwentyFiles() throws Exception {
        for (int i = 0; i < 20; i++) {
            Files.writeString(dir.resolve("doc-" + i + ".txt"),
                    "Alpha beta alpha\nbeta beta gamma\n");
        }
        Map<String, Long> sequential = WordCount.countSequential(dir);
        WordCount.Result parallel = WordCount.analyse(dir, 10, null, false, 4, false, null);
        assertEquals(sequential, parallel.counts());
        assertEquals(20, parallel.filesDone());
        assertFalse(parallel.partial());
        assertEquals(20 * 6L, parallel.words());
    }

    @Test
    @Timeout(5)
    void stopsWithinThreeSecondsWhenGivenATimeout() throws Exception {
        GenerateData.generate(dir, 30, 8_000);
        long start = System.nanoTime();
        WordCount.Result result = WordCount.analyse(dir, 5, 1, true, 8, false, null);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsed < 3_000, "stopped in " + elapsed + " ms, partial=" + result.partial());
    }
}
