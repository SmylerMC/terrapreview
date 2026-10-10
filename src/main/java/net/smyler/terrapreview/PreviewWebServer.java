package net.smyler.terrapreview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/**
 * A very simple web server serving the earth preview image.
 */
public final class PreviewWebServer {
    /**
     * The port the web server listens on.
     */
    public static final int PORT = 8080;

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <title>TerraPreview</title>
            <style>
            html, body {
                height: 100%;
                margin: 0;
            }
            body {
                display: flex;
                align-items: center;
                justify-content: center;
            }
            img {
                image-rendering: pixelated;
                max-width: 100%;
                max-height: 100%;
            }
            </style>
            <script>
            const events = new EventSource('/events');
            events.onmessage = (event) => {
                document.querySelector('img').src = '/preview?v=' + event.data;
            };
            </script>
            </head>
            <body>
            <img src="/preview" alt="Earth preview">
            </body>
            </html>
            """;

    private volatile Preview preview;
    private final AtomicLong version = new AtomicLong();
    private final Object versionChangeMonitor = new Object();
    private volatile boolean running;
    private HttpServer server;

    /**
     * Creates a new web server serving the given preview.
     *
     * @param preview the preview to serve
     */
    public PreviewWebServer(Preview preview) {
        this.preview = preview;
    }

    /**
     * Replaces the preview served by this web server.
     *
     * @param preview the new preview to serve
     */
    public void setPreview(Preview preview) {
        this.preview = preview;
        synchronized (this.versionChangeMonitor) {
            this.version.incrementAndGet();
            this.versionChangeMonitor.notifyAll();
        }
    }

    /**
     * Returns the current version of the preview, incremented on every change.
     *
     * @return the preview version
     */
    public long version() {
        return this.version.get();
    }

    /**
     * Starts the web server on {@link #PORT}.
     *
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        this.running = true;
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);
        this.server.setExecutor(Executors.newCachedThreadPool(
                Thread.ofPlatform().daemon().name("terrapreview-server-").factory()));
        this.server.createContext("/events", this::serveEvents);
        this.server.createContext("/preview", this::serveImage);
        this.server.createContext("/", this::servePreview);
        this.server.start();
    }

    /**
     * Stops the web server.
     */
    public void stop() {
        synchronized (this.versionChangeMonitor) {
            this.running = false;
            this.versionChangeMonitor.notifyAll();
        }
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
    }

    /**
     * Returns the preview page: a minimal centered HTML page that live-reloads the preview image.
     *
     * @return the HTML page content
     */
    public String previewPage() {
        return PAGE;
    }

    private void serveImage(HttpExchange exchange) throws IOException {
        final BufferedImage image = this.preview.render(EarthPreview.DEFAULT_LONGITUDE, EarthPreview.DEFAULT_LATITUDE);
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            ImageIO.write(image, "png", out);
        }
    }

    private void servePreview(HttpExchange exchange) throws IOException {
        byte[] body = this.previewPage().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "text/html; charset=utf-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void serveEvents(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "text/event-stream");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
        long sentVersion = this.version();
        try (OutputStream out = exchange.getResponseBody()) {
            out.write("retry: 1000\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (this.running) {
                synchronized (this.versionChangeMonitor) {
                    while (this.running && this.version() == sentVersion) {
                        this.versionChangeMonitor.wait();
                    }
                    if (!this.running) {
                        return;
                    }
                    sentVersion = this.version();
                }
                out.write(("data: " + sentVersion + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
