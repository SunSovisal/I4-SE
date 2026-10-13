package edu.itc.se.lab01.task1;

import java.util.List;

// start() returns immediately, so the countdowns overlap and "all launched"
// prints before they finish. join() waits, then every state is TERMINATED.
// only artemis is virtual.
//
// --sequential calls run() on main instead. countdowns go one by one and
// "all launched" is after them. apollo prints its own name. gemini and mercury
// print "main" because CountdownTask uses currentThread(). artemis prints
// nothing: VirtualThread.run() does not run the task. nothing was started,
// so join() returns right away and the states stay NEW.
public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo = new Countdown("apollo");
        Thread gemini = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        List<Thread> crew = List.of(apollo, gemini, mercury, artemis);

        if (sequential) {
            for (Thread t : crew) t.run();
        } else {
            for (Thread t : crew) t.start();
        }

        System.out.println("Mission control: all launched");

        for (Thread t : crew) t.join();

        System.out.println("Mission control: all landed");

        for (Thread t : crew) {
            System.out.printf("%-8s virtual=%-5s state=%s%n",
                    t.getName(), t.isVirtual(), t.getState());
        }
    }
}
