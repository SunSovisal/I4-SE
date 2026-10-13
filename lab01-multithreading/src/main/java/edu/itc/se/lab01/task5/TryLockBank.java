package edu.itc.se.lab01.task5;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TryLockBank {
    private final AtomicLong retries = new AtomicLong();

    public boolean transfer(Account from, Account to, long amount) throws InterruptedException {
        if (!from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
            retries.incrementAndGet();
            return false;
        }
        try {
            if (!to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                retries.incrementAndGet();
                return false;
            }
            try {
                Bank.move(from, to, amount);
                return true;
            } finally {
                to.lock.unlock();
            }
        } finally {
            from.lock.unlock();
        }
    }

    public long retries() {
        return retries.get();
    }
}
