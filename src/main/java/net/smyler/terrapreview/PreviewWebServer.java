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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * A very simple web server serving an interactive map of earth chunk tiles.
 */
public final class PreviewWebServer {
    /**
     * The port the web server listens on.
     */
    public static final int PORT = 8080;

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

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <title>TerraPreview</title>
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
            <style>
            html, body {
                height: 100%;
                margin: 0;
            }
            #map {
                height: 100%;
            }
            #map img {
                image-rendering: pixelated;
            }
            </style>
            </head>
            <body>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
            const TILE_SIZE = @TILE_SIZE@;
            const crs = L.Util.extend({}, L.CRS.Simple, {
                transformation: new L.Transformation(1, 0, 1, 0),
            });
            const map = L.map('map', {
                crs: crs,
                minZoom: @MIN_ZOOM@,
                maxZoom: @MAX_ZOOM@,
                zoomControl: false,
                attributionControl: false,
            });
            let tileVersion = 0;
            const makeTiles = () => {
                const layer = L.gridLayer({
                    tileSize: TILE_SIZE,
                    minZoom: @MIN_ZOOM@,
                    maxZoom: @MAX_ZOOM@,
                    keepBuffer: 0,
                    updateWhenIdle: true,
                });
                layer.createTile = (coords) => {
                    const tile = document.createElement('img');
                    tile.src = '/tiles/' + coords.z + '/' + coords.x + '/' + coords.y + '.png?v=' + tileVersion;
                    return tile;
                };
                return layer;
            };
            const tileLayers = [makeTiles()];
            tileLayers[0].addTo(map);
            const events = new EventSource('/events');
            events.onmessage = (event) => {
                tileVersion = event.data;
                const layer = makeTiles();
                layer.addTo(map);
                tileLayers.push(layer);
                while (tileLayers.length > 2) {
                    map.removeLayer(tileLayers.shift());
                }
            };
            map.setView([@CENTER_LAT@, @CENTER_LNG@], @MAX_ZOOM@);
            </script>
            </body>
            </html>
            """;

    private volatile Preview preview;

    private final double[] centerChunk;

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
        this(preview, new double[] {0, 0});
    }

    /**
     * Creates a new web server serving the given preview, initially centered on the given chunk.
     *
     * @param preview the preview to serve
     * @param centerChunk the x and z coordinates of the chunk to center the map on
     */
    public PreviewWebServer(Preview preview, double[] centerChunk) {
        this.preview = preview;
        this.centerChunk = centerChunk.clone();
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
        return PAGE.replace("@TILE_SIZE@", Integer.toString(EarthPreview.TILE_SIZE))
                .replace("@MIN_ZOOM@", Integer.toString(MIN_ZOOM))
                .replace("@MAX_ZOOM@", Integer.toString(MAX_ZOOM))
                .replace("@CENTER_LAT@", Double.toString(centerLat))
                .replace("@CENTER_LNG@", Double.toString(centerLng));
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
