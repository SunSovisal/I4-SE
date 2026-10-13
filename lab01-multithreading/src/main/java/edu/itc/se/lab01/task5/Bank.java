package edu.itc.se.lab01.task5;

public class Bank {
    public void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                move(from, to, amount);
            }
        }
    }

    static void move(Account from, Account to, long amount) {
        if (from.balance >= amount) {
            from.balance -= amount;
            to.balance += amount;
        }
    }
}
