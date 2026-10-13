package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;
import java.util.Deque;

public class BoundedBuffer<T> implements Buffer<T> {
    private final Deque<T> queue = new ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) {
            wait();
        }
        queue.addLast(item);
        if (queue.size() > capacity) {
            throw new AssertionError("buffer exceeded capacity " + capacity);
        }
        notifyAll();
    }

    @Override
    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        if (queue.isEmpty()) {
            throw new AssertionError("take returned from an empty buffer");
        }
        T item = queue.removeFirst();
        notifyAll();
        return item;
    }
}
