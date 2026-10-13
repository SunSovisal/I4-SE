package edu.itc.se.lab01.task3;

import java.util.concurrent.ArrayBlockingQueue;

public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("ArrayBlockingQueue, 20 runs, capacity " + Simulation.CAPACITY);
        for (int run = 1; run <= 20; run++) {
            System.out.println("-- run " + run);
            Simulation.run(
                    new QueueBuffer<>(new ArrayBlockingQueue<>(Simulation.CAPACITY)),
                    run == 1);
        }
    }
}
