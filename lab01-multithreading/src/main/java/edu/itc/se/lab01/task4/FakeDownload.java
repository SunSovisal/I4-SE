package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.concurrent.Callable;

public class FakeDownload implements Callable<String> {
    private final String url;
    private final long sleepMs;
    private final boolean fail;

    public FakeDownload(String url, long sleepMs, boolean fail) {
        this.url = url;
        this.sleepMs = sleepMs;
        this.fail = fail;
    }

    @Override
    public String call() throws Exception {
        if (fail) {
            throw new IOException("boom");
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        return url + " -> " + url.length();
    }
}
