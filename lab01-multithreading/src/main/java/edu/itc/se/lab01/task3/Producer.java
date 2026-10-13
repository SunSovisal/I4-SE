package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;

public class Producer implements Runnable {
    private final Buffer<Order> buffer;
    private final int count;
    private final int startId;

    public Producer(Buffer<Order> buffer, int count, int startId) {
        this.buffer = buffer;
        this.count = count;
        this.startId = startId;
    }

    @Override
    public void run() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            for (int i = 0; i < count; i++) {
                int id = startId + i;
                buffer.put(new Order(id, "item-" + id, random.nextInt(1, 6)));
                Thread.sleep(random.nextInt(0, 21));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
