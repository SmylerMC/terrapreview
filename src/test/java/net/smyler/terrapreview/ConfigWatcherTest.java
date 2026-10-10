package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigWatcherTest {

    private static final long POLL_INTERVAL_MILLIS = 50L;

    private static final long TIMEOUT_SECONDS = 10L;

    private static final long TIMEOUT_NANOS =
            Duration.ofSeconds(TIMEOUT_SECONDS).toNanos();

    @TempDir
    Path configRoot;

    @Test
    void notifiesOnceOnConfigChangeAfterQuietPeriod() throws IOException, InterruptedException {
        AtomicInteger notifications = new AtomicInteger();
        ConfigWatcher watcher = new ConfigWatcher(this.configRoot, notifications::incrementAndGet);
        watcher.start();
        try {
            assertTrue(Files.isDirectory(this.configRoot));

            Files.writeString(this.configRoot.resolve("osm.json5"), "{}");
            assertTrue(awaitNotification(notifications), "watcher did not notify within 10 seconds");
        } finally {
            watcher.stop();
        }
    }

    private static boolean awaitNotification(AtomicInteger notifications) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        while (System.nanoTime() < deadline) {
            if (notifications.get() > 0) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return false;
    }
}
