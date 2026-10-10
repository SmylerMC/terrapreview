package net.smyler.terrapreview;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point of the TerraPreview application.
 */
public final class TerraPreview {

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
     * Returns the greeting message of this application.
     *
     * @return the greeting message
     */
    public static String greeting() {
        return GREETING;
    }

    /**
     * Starts the web server serving the ASCII earth preview and watches the configuration directory.
     *
     * @param args the command line arguments, ignored for now
     * @throws IOException if the web server cannot be started
     */
    public static void main(String[] args) throws IOException {
        PreviewWebServer server = new PreviewWebServer(new EarthPreview());
        server.start();
        System.out.printf("TerraPreview web server listening on http://localhost:%d/%n", PreviewWebServer.PORT);

        ConfigWatcher watcher = new ConfigWatcher(CONFIG_ROOT, () -> {
            System.out.println("Configuration change detected, updating the rendering engine...");
            server.setPreview(new EarthPreview());
        });
        watcher.start();
        System.out.printf("Watching %s for configuration changes%n", CONFIG_ROOT);
    }
}
