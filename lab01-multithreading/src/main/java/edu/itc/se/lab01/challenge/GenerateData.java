package edu.itc.se.lab01.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public final class GenerateData {
    public static final String[] VOCABULARY = {
            "the", "system", "thread", "queue", "lock", "order", "buffer", "prime",
            "download", "virtual", "worker", "scanner", "report", "timeout", "balance",
            "account", "transfer", "consumer", "producer", "atomic", "counter", "word",
            "file", "count", "partial", "poison", "notify", "wait", "future", "pool"
    };

    private GenerateData() {
    }

    public static void generate(Path dir, int files, int wordsPerFile) throws IOException {
        Files.createDirectories(dir);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int file = 0; file < files; file++) {
            StringBuilder text = new StringBuilder(wordsPerFile * 8);
            for (int word = 0; word < wordsPerFile; word++) {
                if (word > 0) {
                    text.append(word % 20 == 0 ? '\n' : ' ');
                }
                text.append(VOCABULARY[random.nextInt(VOCABULARY.length)]);
            }
            text.append('\n');
            Files.writeString(dir.resolve("file-" + file + ".txt"), text.toString());
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "target/word-data");
        int files = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int words = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;
        generate(dir, files, words);
        System.out.println("wrote " + files + " files of " + words + " words to " + dir);
    }
}
