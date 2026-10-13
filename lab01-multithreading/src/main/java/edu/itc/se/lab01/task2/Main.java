package edu.itc.se.lab01.task2;

// expected 8000000. three unsafe runs lost updates:
// 3272318, 5120781, 4928610
public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("unsafe, three runs (expected " + Bench.EXPECTED + ")");
        Bench.run("unsafe-1", new UnsafeCounter());
        Bench.run("unsafe-2", new UnsafeCounter());
        Bench.run("unsafe-3", new UnsafeCounter());

        System.out.println("correct counters");
        Bench.run("synchronized", new SynchronizedCounter());
        Bench.run("lock", new LockCounter());
        Bench.run("atomic", new AtomicCounter());
        Bench.run("long-adder", new LongAdderCounter());
    }
}
