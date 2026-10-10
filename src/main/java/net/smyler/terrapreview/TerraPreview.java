package net.smyler.terrapreview;

import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point of the TerraPreview application.
 */
public final class TerraPreview {
    private static final Logger LOGGER = LogManager.getLogger(TerraPreview.class);

    private TerraPreview() {}

    /**
     * The greeting message printed by this application.
     */
    public static final String GREETING = "Hello, TerraPreview!";

    /**
     * The default terraminusminus configuration directory.
     */
    public static final Path CONFIG_ROOT = Path.of("terraminusminus", "config");

    private static final String USAGE = "Usage: terrapreview [--port <port>] [--projection <name>] [--open-browser]"
            + " | --no-open-browser] [--center-lon <longitude> --center-lat <latitude> | --center-x <x> --center-z <z>]";

    /**
     * Returns the greeting message of this application.
     *
     * @return the greeting message
     */
    public static String greeting() {
        return GREETING;
    }

    /**
     * Starts the web server serving the interactive earth preview map and watches the configuration directory.
     *
     * @param args the command line arguments
     * @throws IOException if the web server cannot be started
     */
    public static void main(String[] args) throws IOException {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
            Projections.settings(options.projection());
        } catch (IllegalArgumentException | NoSuchElementException e) {
            LOGGER.error("{}\n{}", e.getMessage(), USAGE);
            return;
        }
        TerraIntegration.setup();
        EarthPreview preview = new EarthPreview(Projections.settings(options.projection()));
        double[] centerChunk = centerChunkOf(options, preview);
        PreviewWebServer server = new PreviewWebServer(preview, centerChunk, options.port());
        server.start();
        String previewUrl = "http://localhost:" + server.port() + "/";
        LOGGER.info("TerraPreview web server listening on {}", previewUrl);
        if (shouldOpenBrowser(options)) {
            BrowserOpener.open(previewUrl);
        }
        LOGGER.info("Using the {} projection", options.projection());
        ConfigWatcher watcher = new ConfigWatcher(CONFIG_ROOT, () -> {
            LOGGER.info("Configuration change detected, updating the rendering engine...");
            server.setPreview(new EarthPreview(Projections.settings(options.projection())));
        });
        watcher.start();
        LOGGER.info("Watching {} for configuration changes", CONFIG_ROOT);
    }

    static boolean shouldOpenBrowser(CliOptions options) {
        if (options.openBrowser() != null) {
            return options.openBrowser();
        }
        return System.console() != null;
    }

    private static double[] centerChunkOf(CliOptions options, EarthPreview preview) {
        if (options.centerX() != null && options.centerZ() != null) {
            return EarthPreview.centerChunkOfBlock(options.centerX(), options.centerZ());
        }
        if (options.centerLongitude() != null && options.centerLatitude() != null) {
            return preview.centerChunk(options.centerLongitude(), options.centerLatitude());
        }
        return preview.defaultCenterChunk();
    }
}
