package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    static final int ACCOUNTS = 20;
    static final long STARTING_BALANCE = 1_000_000;
    static final int WORKERS = 8;
    static final int TRANSFERS_PER_WORKER = 100_000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("deadlock")) {
            deadlock();
            return;
        }
        orderedProof();
        tryLockProof();
        gracefulShutdown();
    }

    private static void deadlock() throws InterruptedException {
        Account a = new Account(1, 1_000);
        Account b = new Account(2, 1_000);
        Bank bank = new Bank();
        Thread first = new Thread(() -> {
            while (true) {
                bank.transfer(a, b, 1);
            }
        }, "transfer-a-to-b");
        Thread second = new Thread(() -> {
            while (true) {
                bank.transfer(b, a, 1);
            }
        }, "transfer-b-to-a");

        AtomicBoolean reported = new AtomicBoolean();
        Thread watchdog = new Thread(() -> {
            var bean = ManagementFactory.getThreadMXBean();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1_000);
                    long[] ids = bean.findDeadlockedThreads();
                    if (ids != null && reported.compareAndSet(false, true)) {
                        System.out.println("watchdog: deadlock detected");
                        for (ThreadInfo info : bean.getThreadInfo(ids)) {
                            System.out.println("  " + info.getThreadName());
                        }
                        System.out.flush();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        first.start();
        second.start();
        new CountDownLatch(1).await();
    }

    private static void orderedProof() throws InterruptedException {
        Account[] accounts = accounts();
        long before = total(accounts);
        OrderedBank bank = new OrderedBank();
        runFixedTransfers("ordered", accounts, (from, to) -> bank.transfer(from, to, 1));
        long after = total(accounts);
        System.out.println("ordered balance before=" + before + " after=" + after
                + " transfers=" + (WORKERS * TRANSFERS_PER_WORKER));
        if (before != after) {
            throw new IllegalStateException("ordered balance changed");
        }
    }

    private static void tryLockProof() throws InterruptedException {
        Account[] accounts = accounts();
        long before = total(accounts);
        TryLockBank bank = new TryLockBank();
        Thread[] workers = new Thread[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = new Thread(() -> {
                int done = 0;
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (done < TRANSFERS_PER_WORKER && !Thread.currentThread().isInterrupted()) {
                    Account from = accounts[rnd.nextInt(accounts.length)];
                    Account to = accounts[rnd.nextInt(accounts.length)];
                    if (from == to) {
                        continue;
                    }
                    try {
                        if (bank.transfer(from, to, 1)) {
                            done++;
                        } else {
                            Thread.sleep(rnd.nextInt(1, 6));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "trylock-" + (i + 1));
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        long after = total(accounts);
        System.out.println("trylock balance before=" + before + " after=" + after
                + " transfers=" + (WORKERS * TRANSFERS_PER_WORKER)
                + " retries=" + bank.retries());
        if (before != after) {
            throw new IllegalStateException("trylock balance changed");
        }
    }

    private static void gracefulShutdown() throws InterruptedException {
        Account[] accounts = accounts();
        long before = total(accounts);
        TryLockBank bank = new TryLockBank();
        AtomicLong completed = new AtomicLong();
        Thread[] workers = new Thread[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = new Thread(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (!Thread.currentThread().isInterrupted()) {
                    Account from = accounts[rnd.nextInt(accounts.length)];
                    Account to = accounts[rnd.nextInt(accounts.length)];
                    if (from == to) {
                        continue;
                    }
                    try {
                        if (bank.transfer(from, to, 1)) {
                            completed.incrementAndGet();
                        } else {
                            Thread.sleep(rnd.nextInt(1, 6));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "shutdown-" + (i + 1));
            workers[i].start();
        }

        Thread.sleep(2_000);
        for (Thread worker : workers) {
            worker.interrupt();
        }
        for (Thread worker : workers) {
            worker.join(1_000);
            System.out.println(worker.getName() + " alive=" + worker.isAlive());
        }
        long after = total(accounts);
        System.out.println("shutdown transfers=" + completed.get()
                + " balance before=" + before + " after=" + after);
        if (before != after) {
            throw new IllegalStateException("balance changed after shutdown");
        }
        for (Thread worker : workers) {
            if (worker.isAlive()) {
                throw new IllegalStateException(worker.getName() + " still alive after 1s");
            }
        }
    }

    @FunctionalInterface
    private interface Transfer {
        void run(Account from, Account to) throws InterruptedException;
    }

    private static void runFixedTransfers(String name, Account[] accounts, Transfer transfer)
            throws InterruptedException {
        Thread[] workers = new Thread[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = new Thread(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                int done = 0;
                while (done < TRANSFERS_PER_WORKER && !Thread.currentThread().isInterrupted()) {
                    Account from = accounts[rnd.nextInt(accounts.length)];
                    Account to = accounts[rnd.nextInt(accounts.length)];
                    if (from == to) {
                        continue;
                    }
                    try {
                        transfer.run(from, to);
                        done++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, name + "-" + (i + 1));
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
    }

    private static Account[] accounts() {
        Account[] accounts = new Account[ACCOUNTS];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i + 1, STARTING_BALANCE);
        }
        return accounts;
    }

    private static long total(Account[] accounts) {
        long sum = 0;
        for (Account account : accounts) {
            sum += account.balance;
        }
        return sum;
    }
}
