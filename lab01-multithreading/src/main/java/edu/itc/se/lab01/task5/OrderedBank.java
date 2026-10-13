package edu.itc.se.lab01.task5;

public class OrderedBank {
    public void transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) {
                Bank.move(from, to, amount);
            }
        }
    }
}
