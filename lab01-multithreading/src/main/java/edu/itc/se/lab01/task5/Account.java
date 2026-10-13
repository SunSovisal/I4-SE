package edu.itc.se.lab01.task5;

import java.util.concurrent.locks.ReentrantLock;

public class Account {
    final int id;
    long balance;
    final ReentrantLock lock = new ReentrantLock();

    public Account(int id, long balance) {
        this.id = id;
        this.balance = balance;
    }
}
