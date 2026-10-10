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

    private static final String PORT_FLAG = "--port";
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

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
        int port;
        try {
            port = parsePort(args);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            LOGGER.error("{}\nUsage: terrapreview [--port <port>]", e.getMessage());
            return;
        }
        TerraIntegration.setup();
        EarthPreview preview = new EarthPreview();
        PreviewWebServer server = new PreviewWebServer(preview, preview.defaultCenterChunk(), port);
        server.start();
        LOGGER.info("TerraPreview web server listening on http://localhost:{}/", server.port());

        ConfigWatcher watcher = new ConfigWatcher(CONFIG_ROOT, () -> {
            LOGGER.info("Configuration change detected, updating the rendering engine...");
            server.setPreview(new EarthPreview());
        });
        watcher.start();
        LOGGER.info("Watching {} for configuration changes", CONFIG_ROOT);
    }

    /**
     * Extracts the web server port from the command line arguments.
     *
     * <p>Accepts {@code --port <port>}, {@code --port=<port>} and {@code -p <port>}.
     *
     * @param args the command line arguments
     * @return the requested port, or the default port when no port flag is present
     * @throws NoSuchElementException when a port flag is given without a value
     * @throws IllegalArgumentException when the port value is not a valid port number or an unknown flag is given
     */
    static int parsePort(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith(PORT_FLAG + '=')) {
                return parsePortValue(arg.substring(PORT_FLAG.length() + 1));
            }
            if (PORT_FLAG.equals(arg) || "-p".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new NoSuchElementException("Missing value for " + arg);
                }
                return parsePortValue(args[i + 1]);
            }
            throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        return PreviewWebServer.DEFAULT_PORT;
    }

    private static int parsePortValue(String value) {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Invalid port: " + value);
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        return port;
    }
}
