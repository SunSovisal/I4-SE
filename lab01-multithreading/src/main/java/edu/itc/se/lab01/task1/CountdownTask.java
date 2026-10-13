package edu.itc.se.lab01.task1;

public class CountdownTask implements Runnable {
    @Override
    public void run() {
        String[] steps = {"3", "2", "1", "liftoff!"};
        String name = Thread.currentThread().getName();
        try {
            for (String step : steps) {
                System.out.println(name + ": " + step);
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
