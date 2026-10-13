package edu.itc.se.lab01.task3;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("BoundedBuffer, 20 runs, capacity " + Simulation.CAPACITY);
        for (int run = 1; run <= 20; run++) {
            System.out.println("-- run " + run);
            Simulation.run(new BoundedBuffer<>(Simulation.CAPACITY), run == 1);
        }
    }
}
