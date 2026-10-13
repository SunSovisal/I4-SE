package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final Buffer<Order> buffer;
    private final AtomicInteger processed;
    private final boolean verbose;

    public Consumer(Buffer<Order> buffer, AtomicInteger processed, boolean verbose) {
        this.buffer = buffer;
        this.processed = processed;
        this.verbose = verbose;
    }

    @Override
    public void run() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            while (true) {
                Order order = buffer.take();
                if (order.id() == -1) {
                    break;
                }
                Thread.sleep(random.nextInt(5, 31));
                processed.incrementAndGet();
                if (verbose) {
                    System.out.println(Thread.currentThread().getName()
                            + " processed Order[id=" + order.id() + "]");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
