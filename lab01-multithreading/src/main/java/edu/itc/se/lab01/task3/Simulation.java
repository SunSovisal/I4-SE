package edu.itc.se.lab01.task3;

import java.util.concurrent.atomic.AtomicInteger;

final class Simulation {
    static final int PRODUCERS = 2;
    static final int ORDERS_EACH = 50;
    static final int CONSUMERS = 3;
    static final int CAPACITY = 5;
    static final int EXPECTED = PRODUCERS * ORDERS_EACH;

    private Simulation() {
    }

    static void run(Buffer<Order> buffer, boolean verbose) throws InterruptedException {
        AtomicInteger processed = new AtomicInteger();
        Thread[] producers = new Thread[PRODUCERS];
        for (int i = 0; i < PRODUCERS; i++) {
            producers[i] = new Thread(
                    new Producer(buffer, ORDERS_EACH, i * ORDERS_EACH),
                    "producer-" + (i + 1));
            producers[i].start();
        }

        Thread[] consumers = new Thread[CONSUMERS];
        for (int i = 0; i < CONSUMERS; i++) {
            consumers[i] = new Thread(
                    new Consumer(buffer, processed, verbose),
                    "consumer-" + (i + 1));
            consumers[i].start();
        }

        for (Thread producer : producers) {
            producer.join();
        }
        for (int i = 0; i < CONSUMERS; i++) {
            buffer.put(new Order(-1, "poison", 0));
        }
        for (Thread consumer : consumers) {
            consumer.join();
        }

        int got = processed.get();
        System.out.println("processed=" + got + " expected=" + EXPECTED);
        if (got != EXPECTED) {
            throw new IllegalStateException("expected " + EXPECTED + " processed orders but got " + got);
        }
    }
}
