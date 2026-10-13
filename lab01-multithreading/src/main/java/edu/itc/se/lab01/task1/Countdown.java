package edu.itc.se.lab01.task1;

public class Countdown extends Thread {
    public Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        String[] steps = {"3", "2", "1", "liftoff!"};
        try {
            for (String step : steps) {
                System.out.println(getName() + ": " + step);
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
