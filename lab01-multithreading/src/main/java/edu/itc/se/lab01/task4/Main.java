package edu.itc.se.lab01.task4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    static final long LIMIT = 4_000_000;
    static final int CHUNKS = 16;
    static final int DOWNLOADS = 500;

    public static void main(String[] args) throws Exception {
        countPrimes();
        downloadAll("platform-20", platformDownloads());
        downloadAll("virtual", virtualDownloads());
        timeoutOne();
        completableFutureDownloads();
    }

    private static void countPrimes() throws Exception {
        long seqStart = System.nanoTime();
        long sequential = new PrimeCounter(0, LIMIT).call();
        long seqMs = millisSince(seqStart);

        long parStart = System.nanoTime();
        long parallel;
        try (ExecutorService pool = Executors.newFixedThreadPool(
                CHUNKS, Thread.ofPlatform().name("prime-", 0).factory())) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * LIMIT / CHUNKS;
                long to = (i + 1) * LIMIT / CHUNKS;
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }
            parallel = 0;
            for (Future<Long> future : futures) {
                parallel += future.get();
            }
        }
        long parMs = millisSince(parStart);
        System.out.println("primes sequential=" + sequential + " time=" + seqMs + " ms");
        System.out.println("primes parallel  =" + parallel + " time=" + parMs + " ms");
        if (sequential != parallel) {
            throw new IllegalStateException("prime counts differ: " + sequential + " vs " + parallel);
        }
    }

    private static ExecutorService platformDownloads() {
        return Executors.newFixedThreadPool(
                20, Thread.ofPlatform().name("io-platform-", 0).factory());
    }

    private static ExecutorService virtualDownloads() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("io-virtual-", 0).factory());
    }

    private static void downloadAll(String label, ExecutorService pool) throws InterruptedException {
        try (pool) {
            long start = System.nanoTime();
            List<Future<String>> futures = submitDownloads(pool, 200);
            int ok = 0;
            int failed = 0;
            for (Future<String> future : futures) {
                try {
                    future.get();
                    ok++;
                } catch (ExecutionException e) {
                    failed++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
            System.out.println("downloads " + label + " time=" + millisSince(start)
                    + " ms ok=" + ok + " failed=" + failed);
            if (ok != 490 || failed != 10) {
                throw new IllegalStateException(label + " summary ok=" + ok + " failed=" + failed);
            }
        }
    }

    private static void timeoutOne() throws InterruptedException {
        try (ExecutorService pool = virtualDownloads()) {
            Future<String> future = pool.submit(new FakeDownload("https://slow.example/huge", 5_000, false));
            try {
                future.get(1, TimeUnit.SECONDS);
                System.out.println("timeout: completed unexpectedly");
            } catch (TimeoutException e) {
                boolean cancelled = future.cancel(true);
                System.out.println("timed out cancelled=" + cancelled);
            } catch (ExecutionException e) {
                System.out.println("timeout task failed: " + e.getCause());
            }
        }
    }

    private static void completableFutureDownloads() {
        try (ExecutorService pool = virtualDownloads()) {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 1; i <= DOWNLOADS; i++) {
                FakeDownload task = new FakeDownload(
                        "https://example.test/" + i, 200, i % 50 == 0);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, pool).handle((value, error) -> error == null ? value : "failed"));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            long failed = futures.stream().filter(future -> "failed".equals(future.join())).count();
            System.out.println("completableFuture ok=" + (DOWNLOADS - failed) + " failed=" + failed);
            if (failed != 10) {
                throw new IllegalStateException("completableFuture failed=" + failed);
            }
        }
    }

    private static List<Future<String>> submitDownloads(ExecutorService pool, long sleepMs) {
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= DOWNLOADS; i++) {
            futures.add(pool.submit(new FakeDownload(
                    "https://example.test/" + i, sleepMs, i % 50 == 0)));
        }
        return futures;
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
