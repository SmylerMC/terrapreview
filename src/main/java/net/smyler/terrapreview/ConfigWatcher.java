package net.smyler.terrapreview;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Watches the terraminusminus configuration directory for modifications.
 */
public final class ConfigWatcher {

    private static final Logger LOGGER = LogManager.getLogger(ConfigWatcher.class);

    /**
     * How long to wait after the last file change before notifying the listener.
     */
    private static final long QUIET_PERIOD_MILLIS = 500L;

    private final WatchService watchService;
    private final Runnable onChange;
    private final Map<Path, WatchKey> watchedDirectories = new ConcurrentHashMap<>();
    private final Thread thread;
    private volatile boolean running;

    /**
     * Creates a watcher on the given configuration directory, creating it if absent.
     *
     * @param configRoot the configuration directory to watch
     * @param onChange the callback to invoke once changes have settled
     * @throws IOException if the directory cannot be watched
     */
    public ConfigWatcher(Path configRoot, Runnable onChange) throws IOException {
        this.onChange = onChange;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchRecursively(Files.createDirectories(configRoot));
        this.thread = new Thread(this::run, "terrapreview-config-watcher");
        this.thread.setDaemon(true);
    }

    /**
     * Starts watching for configuration changes.
     */
    public void start() {
        this.running = true;
        this.thread.start();
    }

    /**
     * Stops watching for configuration changes.
     *
     * @throws IOException if the underlying watch service cannot be closed
     */
    public void stop() throws IOException {
        this.running = false;
        this.thread.interrupt();
        this.watchService.close();
    }

    private void run() {
        while (this.running) {
            try {
                this.handleKey(this.watchService.take());
                this.waitForQuietPeriod();
                this.onChange.run();
            } catch (ClosedWatchServiceException _) {
                return;
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                LOGGER.error("Failed to watch configuration directory", e);
                return;
            }
        }
    }

    private void waitForQuietPeriod() throws InterruptedException, IOException {
        WatchKey key;
        while ((key = this.watchService.poll(QUIET_PERIOD_MILLIS, TimeUnit.MILLISECONDS)) != null) {
            this.handleKey(key);
        }
    }

    private void handleKey(WatchKey key) throws IOException {
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == OVERFLOW) {
                continue;
            }
            if (event.kind() == ENTRY_CREATE && event.context() instanceof Path path) {
                this.watchRecursivelyIfDirectory(((Path) key.watchable()).resolve(path));
            }
        }
        key.reset();
    }

    private void watchRecursivelyIfDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            this.watchRecursively(path);
        }
    }

    private void watchRecursively(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) stream.filter(Files::isDirectory)::iterator) {
                this.watchedDirectories.put(
                        path, path.register(this.watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE));
            }
        }
    }
}
