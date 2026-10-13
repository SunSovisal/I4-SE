package edu.itc.se.lab01.challenge;

import java.nio.file.Path;

public class Benchmark {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "target/word-data");
        int files = 200;
        int words = 50_000;
        GenerateData.generate(dir, files, words);
        System.out.println("warmup");
        WordCount.analyse(dir, 3, null, false, 2, false, null);
        for (int workers : new int[] {1, 2, 4, 8}) {
            time(dir, workers, false);
        }
        time(dir, files, true);
    }

    private static void time(Path dir, int workers, boolean virtual) throws Exception {
        WordCount.Result result = WordCount.analyse(dir, 3, null, virtual, workers, false, null);
        System.out.println((virtual ? "virtual" : "platform")
                + " workers=" + workers
                + " files=" + result.filesDone() + "/" + result.filesTotal()
                + " words=" + result.words()
                + " elapsed=" + result.elapsedMillis() + " ms"
                + " partial=" + result.partial());
    }
}
