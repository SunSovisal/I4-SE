package edu.itc.se.lab01.task4;

import java.util.concurrent.Callable;

public class PrimeCounter implements Callable<Long> {
    private final long from;
    private final long to;

    public PrimeCounter(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() {
        long primes = 0;
        for (long n = from; n < to; n++) {
            if (isPrime(n)) {
                primes++;
            }
        }
        return primes;
    }

    static boolean isPrime(long n) {
        if (n < 2) {
            return false;
        }
        if (n % 2 == 0) {
            return n == 2;
        }
        for (long d = 3; d * d <= n; d += 2) {
            if (n % d == 0) {
                return false;
            }
        }
        return true;
    }
}
