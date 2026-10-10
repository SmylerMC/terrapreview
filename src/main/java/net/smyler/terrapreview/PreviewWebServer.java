package net.smyler.terrapreview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * A very simple web server serving an interactive map of earth chunk tiles.
 */
public final class PreviewWebServer {
    /**
     * The default port the web server listens on.
     */
    public static final int DEFAULT_PORT = 8080;

    /**
     * The lowest leaflet zoom level, two unzoom levels below the native chunk pixel size.
     */
    private static final int MIN_ZOOM = -2;

    /**
     * The highest leaflet zoom level; keeps the number of visible tiles to a few dozen at most.
     */
    private static final int MAX_ZOOM = 3;

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final double HALF_CHUNK = 0.5D;

    private static final Pattern TILE_PATTERN = Pattern.compile("/tiles/(-?\\d+)/(-?\\d+)/(-?\\d+)\\.png");

    private static final int ZOOM_CAPTURE_GROUP = 1;

    private static final String INDEX_RESOURCE = "/web/index.html";
    private static final String PAGE_TEMPLATE = readResource(INDEX_RESOURCE);

    private volatile Preview preview;

    private final double[] centerChunk;

    private final int port;

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
        this(preview, new double[] {0, 0}, DEFAULT_PORT);
    }

    /**
     * Creates a new web server serving the given preview, initially centered on the given chunk.
     *
     * @param preview the preview to serve
     * @param centerChunk the x and z coordinates of the chunk to center the map on
     */
    public PreviewWebServer(Preview preview, double[] centerChunk) {
        this(preview, centerChunk, DEFAULT_PORT);
    }

    /**
     * Creates a new web server serving the given preview, initially centered on the given chunk, listening on the given port.
     *
     * @param preview the preview to serve
     * @param centerChunk the x and z coordinates of the chunk to center the map on
     * @param port the port to listen on
     */
    public PreviewWebServer(Preview preview, double[] centerChunk, int port) {
        this.preview = preview;
        this.centerChunk = centerChunk.clone();
        this.port = port;
    }

    /**
     * Returns the port the web server listens on.
     *
     * @return the listening port
     */
    public int port() {
        return this.port;
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
     * Starts the web server on the configured port.
     *
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        this.running = true;
        this.server = HttpServer.create(new InetSocketAddress(this.port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool(
                Thread.ofPlatform().daemon().name("terrapreview-server-").factory()));
        this.server.createContext("/events", this::serveEvents);
        this.server.createContext("/tiles", this::serveTile);
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
     * Returns the preview page: a leaflet map of chunk tiles that live-reloads on preview changes.
     *
     * @return the HTML page content
     */
    public String previewPage() {

        double centerLat = (this.centerChunk[1] + HALF_CHUNK) * EarthPreview.TILE_SIZE;
        double centerLng = (this.centerChunk[0] + HALF_CHUNK) * EarthPreview.TILE_SIZE;
        return PAGE_TEMPLATE
                .replace("@TILE_SIZE@", Integer.toString(EarthPreview.TILE_SIZE))
                .replace("@MIN_ZOOM@", Integer.toString(MIN_ZOOM))
                .replace("@MAX_ZOOM@", Integer.toString(MAX_ZOOM))
                .replace("@CENTER_LAT@", Double.toString(centerLat))
                .replace("@CENTER_LNG@", Double.toString(centerLng));
    }

    private static String readResource(String name) {
        try (InputStream in = PreviewWebServer.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing web resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read web resource: " + name, e);
        }
    }

    private void serveTile(HttpExchange exchange) throws IOException {
        Matcher match = TILE_PATTERN.matcher(exchange.getRequestURI().getPath());
        if (!match.matches()) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1L);
            return;
        }
        int zoom = Integer.parseInt(match.group(ZOOM_CAPTURE_GROUP));
        int tileX = Integer.parseInt(match.group(ZOOM_CAPTURE_GROUP + 1));
        int tileZ = Integer.parseInt(match.group(ZOOM_CAPTURE_GROUP + 2));
        final BufferedImage tile = this.preview.renderTile(zoom, tileX, tileZ);
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            ImageIO.write(tile, "png", out);
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
