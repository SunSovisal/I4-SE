package edu.itc.se.lab01.challenge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class WordCount {
    public static final int QUEUE_CAPACITY = 16;
    static final Path POISON = Path.of("/__lab01_poison__");
    static final Pattern SPLIT = Pattern.compile("[^\\p{L}]+");

    public record Result(
            Map<String, Long> counts,
            int filesDone,
            int filesTotal,
            long words,
            long elapsedMillis,
            boolean partial,
            String mode) {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Run run = new Run(options);
        Thread hook = new Thread(run::shutdownFromHook, "wordcount-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            Result result = run.await();
            run.print(result);
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException alreadyExiting) {
                // already shutting down, hook runs instead
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Map<String, Long> countSequential(Path dir) throws IOException {
        Map<String, Long> counts = new HashMap<>();
        for (Path file : listFiles(dir)) {
            countFile(file, word -> counts.merge(word, 1L, Long::sum));
        }
        return counts;
    }

    public static Result analyse(Path dir, int top, Integer timeoutSeconds, boolean virtual,
            int workers, boolean progress, Path csv) throws Exception {
        return new Run(new Options(dir, top, timeoutSeconds, virtual, workers, progress, csv)).await();
    }

    static List<Path> listFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    static void countFile(Path path, Consumer<String> wordConsumer) throws IOException {
        try (var lines = Files.lines(path)) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                for (String raw : SPLIT.split(iterator.next())) {
                    if (!raw.isEmpty()) {
                        wordConsumer.accept(raw.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    private record Options(Path dir, int top, Integer timeoutSeconds, boolean virtual, int workers,
            boolean progress, Path csv) {
        static Options parse(String[] args) {
            Path dir = null;
            int top = 10;
            Integer timeout = null;
            boolean virtual = false;
            int workers = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--top" -> top = Integer.parseInt(args[++i]);
                    case "--timeout" -> timeout = Integer.parseInt(args[++i]);
                    case "--virtual" -> virtual = true;
                    case "--workers" -> workers = Integer.parseInt(args[++i]);
                    default -> dir = Path.of(args[i]);
                }
            }
            if (dir == null) {
                System.out.println("Usage: WordCount <dir> [--top N] [--timeout SECONDS] [--virtual] [--workers N]");
                System.exit(1);
            }
            return new Options(dir, top, timeout, virtual, workers, true, Path.of("result.csv"));
        }
    }

    private static final class Run {
        private final Options options;
        private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private final Map<String, LongAdder> counts = new java.util.concurrent.ConcurrentHashMap<>();
        private final LongAdder words = new LongAdder();
        private final AtomicInteger filesDone = new AtomicInteger();
        private final AtomicInteger filesTotal = new AtomicInteger(-1);
        private final AtomicBoolean stop = new AtomicBoolean();
        private final AtomicBoolean printed = new AtomicBoolean();
        private final CountDownLatch workersDone;
        private final Thread scanner;
        private final Thread[] workers;
        private final Thread reporter;
        private final long started = System.nanoTime();
        private final String mode;

        Run(Options options) {
            this.options = options;
            this.workersDone = new CountDownLatch(options.workers);
            this.mode = (options.virtual ? "virtual" : "platform") + " x " + options.workers;
            this.scanner = new Thread(this::scan, "scanner");
            this.workers = new Thread[options.workers];
            for (int i = 0; i < options.workers; i++) {
                var builder = options.virtual
                        ? Thread.ofVirtual().name("worker-" + (i + 1))
                        : Thread.ofPlatform().name("worker-" + (i + 1));
                workers[i] = builder.unstarted(this::work);
            }
            this.reporter = new Thread(this::report, "reporter");
            this.reporter.setDaemon(true);
        }

        Result await() throws InterruptedException {
            scanner.start();
            for (Thread worker : workers) {
                worker.start();
            }
            reporter.start();
            boolean finished = options.timeoutSeconds == null
                    ? awaitWorkers()
                    : workersDone.await(options.timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                cancel();
            } else {
                scanner.join();
                for (Thread worker : workers) {
                    worker.join();
                }
            }
            reporter.interrupt();
            reporter.join(500);
            return snapshot(stop.get() || !finished);
        }

        void shutdownFromHook() {
            cancel();
            print(snapshot(true));
        }

        private boolean awaitWorkers() throws InterruptedException {
            workersDone.await();
            return true;
        }

        private void cancel() {
            if (!stop.compareAndSet(false, true)) {
                return;
            }
            scanner.interrupt();
            for (Thread worker : workers) {
                worker.interrupt();
            }
            reporter.interrupt();
            joinWithin(2_000);
        }

        private void joinWithin(long budgetMs) {
            long deadline = System.nanoTime() + budgetMs * 1_000_000L;
            try {
                scanner.join(remaining(deadline));
                for (Thread worker : workers) {
                    worker.join(remaining(deadline));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static long remaining(long deadlineNanos) {
            return Math.max(1, (deadlineNanos - System.nanoTime()) / 1_000_000L);
        }

        private void scan() {
            try {
                List<Path> files = listFiles(options.dir);
                filesTotal.set(files.size());
                for (Path file : files) {
                    if (stop.get() || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    queue.put(file);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                if (!stop.get() && !Thread.currentThread().isInterrupted()) {
                    for (int i = 0; i < workers.length; i++) {
                        try {
                            queue.put(POISON);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        private void work() {
            try {
                while (!stop.get() && !Thread.currentThread().isInterrupted()) {
                    Path path;
                    try {
                        path = queue.poll(200, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (path == null) {
                        continue;
                    }
                    if (path == POISON) {
                        return;
                    }
                    try {
                        countFile(path, word -> {
                            counts.computeIfAbsent(word, key -> new LongAdder()).increment();
                            words.increment();
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    if (!Thread.currentThread().isInterrupted()) {
                        filesDone.incrementAndGet();
                    }
                }
            } finally {
                workersDone.countDown();
            }
        }

        private void report() {
            try {
                while (!workersDone.await(500, TimeUnit.MILLISECONDS)) {
                    if (options.progress) {
                        printProgress();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void printProgress() {
            long elapsed = Math.max(1, elapsedMillis());
            long seen = words.sum();
            long rate = seen * 1000 / elapsed;
            int total = filesTotal.get();
            String totalText = total < 0 ? "?" : Integer.toString(total);
            // entrySet() is weakly consistent, so this top 3 can miss an update.
            // fine for a progress line, and it does not lock the workers.
            String top = counts.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<String, LongAdder> entry) -> entry.getValue().sum())
                            .reversed())
                    .limit(3)
                    .map(entry -> entry.getKey() + ":" + entry.getValue().sum())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("-");
            System.out.println("files " + filesDone.get() + "/" + totalText
                    + " words=" + seen + " rate=" + rate + "/s top3=" + top);
        }

        private Result snapshot(boolean partial) {
            Map<String, Long> copy = new HashMap<>();
            counts.forEach((word, adder) -> copy.put(word, adder.sum()));
            int total = Math.max(filesTotal.get(), 0);
            return new Result(copy, filesDone.get(), total, words.sum(), elapsedMillis(), partial, mode);
        }

        void print(Result result) {
            if (!printed.compareAndSet(false, true)) {
                return;
            }
            if (result.partial()) {
                System.out.println("[PARTIAL]");
            }
            System.out.println("mode=" + result.mode());
            System.out.println("files=" + result.filesDone() + "/" + result.filesTotal()
                    + " words=" + result.words() + " elapsed=" + result.elapsedMillis() + " ms");
            List<Map.Entry<String, Long>> ranked = ranked(result.counts());
            int shown = Math.min(options.top, ranked.size());
            for (int i = 0; i < shown; i++) {
                Map.Entry<String, Long> entry = ranked.get(i);
                double percent = result.words() == 0 ? 0 : 100.0 * entry.getValue() / result.words();
                System.out.printf("%2d. %-16s %8d %6.2f%%%n", i + 1, entry.getKey(), entry.getValue(), percent);
            }
            if (options.csv != null) {
                writeCsv(options.csv, ranked);
            }
        }

        private static void writeCsv(Path csv, List<Map.Entry<String, Long>> ranked) {
            StringBuilder text = new StringBuilder("word,count\n");
            for (Map.Entry<String, Long> entry : ranked) {
                text.append(entry.getKey()).append(',').append(entry.getValue()).append('\n');
            }
            try {
                Files.writeString(csv, text.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static List<Map.Entry<String, Long>> ranked(Map<String, Long> counts) {
            List<Map.Entry<String, Long>> ranked = new ArrayList<>(counts.entrySet());
            ranked.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                    .thenComparing(Map.Entry::getKey));
            return ranked;
        }

        private long elapsedMillis() {
            return (System.nanoTime() - started) / 1_000_000L;
        }
    }
}
