package edu.itc.se.lab01.task2;

public final class Bench {
    public static final int THREADS = 8;
    public static final int ITERATIONS = 1_000_000;
    public static final long EXPECTED = (long) THREADS * ITERATIONS;

    private Bench() {
    }

    public static long run(String label, Counter counter) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(() -> {
                for (int n = 0; n < ITERATIONS; n++) {
                    counter.increment();
                }
            }, label + "-" + i);
        }

        long start = System.nanoTime();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long value = counter.get();
        System.out.printf("%-18s value=%8d correct=%-5s elapsed=%d ms%n",
                label, value, value == EXPECTED, elapsedMs);
        return value;
    }
}
