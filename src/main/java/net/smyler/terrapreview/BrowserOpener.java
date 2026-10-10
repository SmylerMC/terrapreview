package net.smyler.terrapreview;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Opens the user's browser at a URL.
 */
public final class BrowserOpener {
    private static final Logger LOGGER = LogManager.getLogger(BrowserOpener.class);

    private BrowserOpener() {}

    /**
     * Opens the user's browser at the given URL, if possible.
     *
     * <p>Failures are logged and swallowed: opening the browser is a convenience and must not stop the server.
     *
     * @param url the URL to open
     */
    public static void open(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            LOGGER.warn("Cannot open a browser on this system, please visit {} manually", url);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not open the browser at {}, please visit it manually", url, e);
        }
    }
}
