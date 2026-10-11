package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that the configuration watcher keeps notifying after a change callback has failed.
 */
class ConfigWatcherSurvivesCallbackFailureTest {

    private static final long POLL_INTERVAL_MILLIS = 50L;
    private static final long TIMEOUT_SECONDS = 10L;

    private static final long TIMEOUT_NANOS =
            Duration.ofSeconds(TIMEOUT_SECONDS).toNanos();

    @TempDir
    Path configRoot;

    private final AtomicInteger notifications = new AtomicInteger();

    @Test
    void keepsNotifyingAfterTheCallbackThrows() throws IOException, InterruptedException {
        ConfigWatcher watcher = new ConfigWatcher(this.configRoot, () -> {
            notifications.incrementAndGet();
            if (notifications.get() == 1) {
                throw new IllegalStateException("simulated reload failure");
            }
        });
        watcher.start();
        try {
            Files.writeString(this.configRoot.resolve("osm.json5"), "{}");
            assertTrue(this.awaitNotifications(1), "the first change was not notified");
            Files.writeString(this.configRoot.resolve("osm.json5"), "{\"a\":1}");
            assertTrue(this.awaitNotifications(2), "the watcher stopped notifying after a callback failure");
        } finally {
            watcher.stop();
        }
    }

    private boolean awaitNotifications(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        while (System.nanoTime() < deadline) {
            if (notifications.get() >= expected) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return false;
    }
}
