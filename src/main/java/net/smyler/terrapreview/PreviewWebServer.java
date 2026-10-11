package net.smyler.terrapreview;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
     * A port value requesting an ephemeral port, assigned by the operating system on start.
     */
    public static final int ANY_PORT = 0;
    /**
     * The lowest leaflet zoom level, five unzoom levels below the native chunk pixel size.
     */
    private static final int MIN_ZOOM = -5;

    /**
     * The highest leaflet zoom level; keeps the number of visible tiles to a few dozen at most.
     */
    private static final int MAX_ZOOM = 3;

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final double HALF_CHUNK = 0.5D;

    private static final Pattern TILE_PATTERN = Pattern.compile("/tiles/(-?\\d+)/(-?\\d+)/(-?\\d+)\\.png");
    private static final Pattern GEO_PATTERN = Pattern.compile("/geo/(-?\\d+(?:\\.\\d+)?)/(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LOCATE_PATTERN = Pattern.compile("/locate/(-?\\d+(?:\\.\\d+)?)/(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern SEARCH_QUERY_PATTERN = Pattern.compile("q=([^&]*)");

    private static final Gson GSON = new Gson();

    private static final String FEATURE_SEPARATOR = ",";

    private static final String JSON_OBJECT_END = "}";

    private static final String GEOCODER_NAME = "Photon";

    private static final int ZOOM_CAPTURE_GROUP = 1;

    private static final String INDEX_RESOURCE = "/web/index.html";
    private static final String PAGE_TEMPLATE = readResource(INDEX_RESOURCE);

    private volatile Preview preview;

    private final PhotonGeocoder geocoder;

    private final double[] centerChunk;

    private final int port;

    private volatile int boundPort;

    private final AtomicLong version = new AtomicLong();
    private final Object versionChangeMonitor = new Object();
    private volatile String error;
    private volatile boolean running;
    private volatile String instanceId;
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
        this(preview, centerChunk, port, new PhotonGeocoder());
    }

    /**
     * Creates a new web server serving the given preview, initially centered on the given chunk, listening on the
     * given port, searching places with the given geocoder.
     *
     * @param preview the preview to serve
     * @param centerChunk the x and z coordinates of the chunk to center the map on
     * @param port the port to listen on
     * @param geocoder the geocoder to use for place searches
     */
    public PreviewWebServer(Preview preview, double[] centerChunk, int port, PhotonGeocoder geocoder) {
        this.preview = preview;
        this.centerChunk = centerChunk.clone();
        this.port = port;
        this.geocoder = geocoder;
    }

    /**
     * Returns the port the web server listens on.
     *
     * <p>When constructed with {@value #ANY_PORT}, this returns that value until the server is started,
     * then the ephemeral port assigned by the operating system.
     *
     * @return the listening port
     */
    public int port() {
        return this.boundPort != 0 ? this.boundPort : this.port;
    }

    /**
     * Replaces the preview served by this web server.
     *
     * @param preview the new preview to serve
     */
    public void setPreview(Preview preview) {
        this.preview = preview;
        this.error = null;
        synchronized (this.versionChangeMonitor) {
            this.version.incrementAndGet();
            this.versionChangeMonitor.notifyAll();
        }
    }

    /**
     * Reports an error that prevents the preview from being updated, shown to the web UI.
     *
     * <p>The previously served preview is kept, so the map stays usable while the error is displayed.
     *
     * @param message a human readable description of the error
     */
    public void setError(String message) {
        this.error = message;
        synchronized (this.versionChangeMonitor) {
            this.version.incrementAndGet();
            this.versionChangeMonitor.notifyAll();
        }
    }

    /**
     * Returns the current error message, or null when the preview is up to date.
     *
     * @return the current error message, or null
     */
    public String error() {
        return this.error;
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
        this.instanceId = UUID.randomUUID().toString();
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), this.port), 0);
        this.boundPort = this.server.getAddress().getPort();
        this.server.setExecutor(Executors.newCachedThreadPool(
                Thread.ofPlatform().daemon().name("terrapreview-server-").factory()));
        this.server.createContext("/events", this::serveEvents);
        this.server.createContext("/error", this::serveError);
        this.server.createContext("/geo", this::serveGeo);
        this.server.createContext("/search", this::serveSearch);
        this.server.createContext("/locate", this::serveLocate);
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
                .replace("@CHUNK_SIZE@", Integer.toString(EarthPreview.CHUNK_SIZE))
                .replace("@MIN_ZOOM@", Integer.toString(MIN_ZOOM))
                .replace("@MAX_ZOOM@", Integer.toString(MAX_ZOOM))
                .replace("@CENTER_LAT@", Double.toString(centerLat))
                .replace("@CENTER_LNG@", Double.toString(centerLng))
                .replace("@GEOCODER_NAME@", GEOCODER_NAME)
                .replace("@GEOCODER_URL@", PhotonGeocoder.PHOTON_URL);
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

    private void serveLocate(HttpExchange exchange) throws IOException {
        Matcher match = LOCATE_PATTERN.matcher(exchange.getRequestURI().getPath());
        if (!match.matches()) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1L);
            return;
        }
        double longitude = Double.parseDouble(match.group(ZOOM_CAPTURE_GROUP));
        double latitude = Double.parseDouble(match.group(ZOOM_CAPTURE_GROUP + 1));
        double[] block;
        try {
            block = this.preview.geoToBlock(longitude, latitude);
        } catch (IllegalArgumentException _) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1L);
            return;
        } catch (UnsupportedOperationException _) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_IMPLEMENTED, -1L);
            return;
        }
        sendJson(exchange, "{\"x\":" + block[0] + ",\"z\":" + block[1] + JSON_OBJECT_END);
    }

    private void serveSearch(HttpExchange exchange) throws IOException {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Matcher match = rawQuery == null ? null : SEARCH_QUERY_PATTERN.matcher(rawQuery);
        String query = match != null && match.find()
                ? URLDecoder.decode(match.group(ZOOM_CAPTURE_GROUP), StandardCharsets.UTF_8)
                        .strip()
                : "";
        List<PhotonGeocoder.Place> places;
        try {
            places = this.geocoder.search(query);
        } catch (IOException | JsonParseException _) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAVAILABLE, -1L);
            return;
        }
        String features = places.stream()
                .map(PreviewWebServer::searchResultJson)
                .collect(Collectors.joining(FEATURE_SEPARATOR, "[", "]"));
        sendJson(exchange, "{\"results\":" + features + JSON_OBJECT_END);
    }

    private void serveGeo(HttpExchange exchange) throws IOException {
        Matcher match = GEO_PATTERN.matcher(exchange.getRequestURI().getPath());
        if (!match.matches()) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1L);
            return;
        }
        double x = Double.parseDouble(match.group(ZOOM_CAPTURE_GROUP));
        double z = Double.parseDouble(match.group(ZOOM_CAPTURE_GROUP + 1));
        double[] geo;
        try {
            geo = this.preview.blockToGeo(x, z);
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1L);
            return;
        }
        byte[] body = ("{\"longitude\":" + geo[0] + ",\"latitude\":" + geo[1] + JSON_OBJECT_END)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "application/json");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void serveError(HttpExchange exchange) throws IOException {
        String message = this.error;
        byte[] body;
        if (message == null) {
            body = "null".getBytes(StandardCharsets.UTF_8);
        } else {
            String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            body = ('"' + escaped + '"').getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String searchResultJson(PhotonGeocoder.Place place) {
        return "{\"name\":" + GSON.toJson(place.name())
                + ",\"description\":" + GSON.toJson(String.join(", ", place.description()))
                + ",\"kind\":" + GSON.toJson(place.kind())
                + ",\"longitude\":" + place.longitude()
                + ",\"latitude\":" + place.latitude()
                + JSON_OBJECT_END;
    }

    private static void sendJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, "application/json");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
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
            out.write(("event: hello\ndata: " + this.instanceId + "\n\n").getBytes(StandardCharsets.UTF_8));
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
