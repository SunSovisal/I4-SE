package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.LongAdder;

// quicker under contention: threads update their own cells instead of one
// AtomicLong. sum() adds the cells when we read.
public class LongAdderCounter implements Counter {
    private final LongAdder count = new LongAdder();

    @Override
    public void increment() {
        count.increment();
    }

    @Override
    public long get() {
        return count.sum();
    }
}
