package edu.itc.se.lab01.task3;

import java.util.concurrent.BlockingQueue;

public class QueueBuffer<T> implements Buffer<T> {
    private final BlockingQueue<T> queue;

    public QueueBuffer(BlockingQueue<T> queue) {
        this.queue = queue;
    }

    @Override
    public void put(T item) throws InterruptedException {
        queue.put(item);
    }

    @Override
    public T take() throws InterruptedException {
        return queue.take();
    }
}
