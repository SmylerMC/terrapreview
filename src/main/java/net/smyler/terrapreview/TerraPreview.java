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

    /**
     * The directory the Minecraft client JAR is cached in.
     */
    public static final Path CLIENT_JAR_CACHE = Path.of("terraminusminus", "terrapreview");

    private static final String USAGE =
            "Usage: terrapreview [--port <port>] [--projection <name>] [--mc-version <version>]"
                    + " [--open-browser] | --no-open-browser]"
                    + " [--center-lon <longitude> --center-lat <latitude> | --center-x <x> --center-z <z>]";

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
        BlockTextures textures = loadBlockTextures(options.mcVersion());
        Preview preview;
        String configurationError = null;
        try {
            preview = new EarthPreview(GeneratorSettingsFile.tryLoad(CONFIG_ROOT), textures);
            // CHECKSTYLE:OFF: IllegalCatch - an invalid user configuration must not prevent the server from starting
        } catch (IOException | RuntimeException e) {
            LOGGER.error("The Terra-- configuration is invalid, serving an empty preview", e);
            preview = new UnavailablePreview();
            configurationError = e.getMessage();
            // CHECKSTYLE:ON
        }
        double[] centerChunk = centerChunkOf(options, preview);
        PreviewWebServer server = new PreviewWebServer(preview, centerChunk, options.port());
        server.start();
        if (configurationError != null) {
            server.setError("The Terra-- configuration is invalid: " + configurationError);
        }
        String previewUrl = "http://localhost:" + server.port() + "/";
        LOGGER.info("TerraPreview web server listening on {}", previewUrl);
        if (shouldOpenBrowser(options)) {
            BrowserOpener.open(previewUrl);
        }
        ConfigWatcher watcher = new ConfigWatcher(CONFIG_ROOT, () -> {
            LOGGER.info("Configuration change detected, updating the rendering engine...");
            try {
                server.setPreview(new EarthPreview(GeneratorSettingsFile.tryLoad(CONFIG_ROOT), textures));
                LOGGER.info("The rendering engine was updated");
                // CHECKSTYLE:OFF: IllegalCatch - an invalid user configuration must not kill the watcher
            } catch (IOException | RuntimeException e) {
                LOGGER.error("The new configuration is invalid, keeping the previous world", e);
                server.setError("Could not reload the Terra-- configuration: " + e.getMessage());
                // CHECKSTYLE:ON
            }
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

    private static BlockTextures loadBlockTextures(String mcVersion) {
        try {
            MinecraftClientJar clientJar = MinecraftClientJar.ensureDownloaded(CLIENT_JAR_CACHE, mcVersion);
            LOGGER.info("Using Minecraft {} client JAR at {}", clientJar.version(), clientJar.path());
            return new BlockTextures(clientJar.path());
        } catch (IOException e) {
            LOGGER.error("Failed to load block textures, falling back to flat colors", e);
            return null;
        }
    }

    private static double[] centerChunkOf(CliOptions options, Preview preview) {
        if (options.centerX() != null && options.centerZ() != null) {
            return EarthPreview.centerChunkOfBlock(options.centerX(), options.centerZ());
        }
        if (preview instanceof EarthPreview earthPreview) {
            if (options.centerLongitude() != null && options.centerLatitude() != null) {
                return earthPreview.centerChunk(options.centerLongitude(), options.centerLatitude());
            }
            return earthPreview.defaultCenterChunk();
        }
        return new double[] {0, 0};
    }
}
