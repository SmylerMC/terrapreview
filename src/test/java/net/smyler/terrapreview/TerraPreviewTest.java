package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TerraPreviewTest {

    private static final long TIMEOUT_SECONDS = 10L;

    private static final long TIMEOUT_NANOS =
            Duration.ofSeconds(TIMEOUT_SECONDS).toNanos();

    private static final BufferedImage STUB_TILE = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String DATA_PREFIX = "data:";

    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final double GEO_TEST_SCALE = 1000D;
    private static final double GEO_TEST_SCALE = 1000D;

    private static final String BASE_URL = "http://localhost:" + PreviewWebServer.DEFAULT_PORT;

    private static final int TEST_PORT = 4242;

    private static final String PORT_FLAG = "--port";

    private static final String[] PORT_ARGS = {PORT_FLAG, String.valueOf(TEST_PORT)};

    private static final String PROJECTION_FLAG = "--projection";

    private static final String EQUALS_FORM = "=";

    private static final String CENTER_LON_FLAG = "--center-lon";

    private static final String CENTER_LAT_FLAG = "--center-lat";

    private static final String CENTER_X_FLAG = "--center-x";

    private static final String CENTER_Z_FLAG = "--center-z";

    private static final String OPEN_BROWSER_FLAG = "--open-browser";

    private static final String MC_VERSION_FLAG = "--mc-version";

    private static final String TEST_MC_VERSION = "1.21.8";

    private static final String NO_OPEN_BROWSER_FLAG = "--no-open-browser";

    private static final String CENTER_LON = "12.5";

    private static final String CENTER_LAT = "-45.25";

    private static final double CENTER_LON_VALUE = 12.5D;

    private static final double CENTER_LAT_VALUE = -45.25D;

    private static final String CENTER_X = "1000";

    private static final String CENTER_Z = "-2000";

    private static final double CENTER_X_VALUE = 1000.0D;

    private static final double CENTER_Z_VALUE = -2000.0D;

    static {
        STUB_TILE.setRGB(0, 0, Color.RED.getRGB());
    }

    @Test
    void greetingIsHelloTerraPreview() {
        assertEquals("Hello, TerraPreview!", TerraPreview.greeting());
    }

    @Test
    void optionsDefaultToPort8080AndBteProjection() {
        assertEquals(CliOptions.DEFAULT, CliOptions.parse(new String[0]));
    }

    @Test
    void optionsAcceptPortInLongForm() {
        assertEquals(TEST_PORT, CliOptions.parse(PORT_ARGS).port());
    }

    @Test
    void optionsAcceptPortInEqualsForm() {
        assertEquals(
                TEST_PORT,
                CliOptions.parse(new String[] {PORT_FLAG + EQUALS_FORM + TEST_PORT})
                        .port());
    }

    @Test
    void optionsAcceptPortInShortForm() {
        assertEquals(
                TEST_PORT,
                CliOptions.parse(new String[] {"-p", String.valueOf(TEST_PORT)}).port());
    }

    @Test
    void optionsAcceptProjectionInLongForm() {
        assertEquals(
                Projections.TERRA_PLUS_PLUS,
                CliOptions.parse(new String[] {PROJECTION_FLAG, Projections.TERRA_PLUS_PLUS})
                        .projection());
    }

    @Test
    void optionsAcceptProjectionInEqualsForm() {
        assertEquals(
                "equirectangular",
                CliOptions.parse(new String[] {PROJECTION_FLAG + "=equirectangular"})
                        .projection());
    }

    @Test
    void optionsAcceptProjectionInShortForm() {
        assertEquals(
                Projections.TERRA_PLUS_PLUS,
                CliOptions.parse(new String[] {"-P", Projections.TERRA_PLUS_PLUS})
                        .projection());
    }

    @Test
    void optionsDefaultToTheLatestMinecraftVersion() {
        assertEquals(null, CliOptions.parse(new String[0]).mcVersion());
    }

    @Test
    void optionsAcceptMcVersionInLongForm() {
        assertEquals(
                TEST_MC_VERSION,
                CliOptions.parse(new String[] {MC_VERSION_FLAG, TEST_MC_VERSION})
                        .mcVersion());
    }

    @Test
    void optionsAcceptMcVersionInEqualsForm() {
        assertEquals(
                TEST_MC_VERSION,
                CliOptions.parse(new String[] {MC_VERSION_FLAG + EQUALS_FORM + TEST_MC_VERSION})
                        .mcVersion());
    }

    @Test
    void optionsAcceptPortAndProjectionTogether() {
        CliOptions options = CliOptions.parse(
                new String[] {PORT_FLAG, String.valueOf(TEST_PORT), PROJECTION_FLAG, Projections.TERRA_PLUS_PLUS});
        assertEquals(TEST_PORT, options.port());
        assertEquals(Projections.TERRA_PLUS_PLUS, options.projection());
    }

    @Test
    void optionsRejectNonNumericPortValues() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {PORT_FLAG, "abc"}));
    }

    @Test
    void optionsRejectOutOfRangePortValues() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {PORT_FLAG, "0"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {PORT_FLAG, "99999"}));
    }

    @Test
    void optionsRejectMissingFlagValues() {
        assertThrows(NoSuchElementException.class, () -> CliOptions.parse(new String[] {PORT_FLAG}));
        assertThrows(NoSuchElementException.class, () -> CliOptions.parse(new String[] {PROJECTION_FLAG}));
    }

    @Test
    void optionsRejectUnknownArguments() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"--unknown"}));
    }

    @Test
    void optionsAcceptGeographicCenter() {
        CliOptions options = CliOptions.parse(new String[] {CENTER_LON_FLAG, CENTER_LON, CENTER_LAT_FLAG, CENTER_LAT});
        assertEquals(CENTER_LON_VALUE, options.centerLongitude());
        assertEquals(CENTER_LAT_VALUE, options.centerLatitude());
        assertNull(options.centerX());
        assertNull(options.centerZ());
    }

    @Test
    void optionsAcceptMinecraftCenter() {
        CliOptions options = CliOptions.parse(new String[] {CENTER_X_FLAG, CENTER_X, CENTER_Z_FLAG, CENTER_Z});
        assertEquals(CENTER_X_VALUE, options.centerX());
        assertEquals(CENTER_Z_VALUE, options.centerZ());
        assertNull(options.centerLongitude());
        assertNull(options.centerLatitude());
    }

    @Test
    void optionsAcceptCenterInEqualsForm() {
        CliOptions options = CliOptions.parse(
                new String[] {CENTER_LON_FLAG + EQUALS_FORM + CENTER_LON, CENTER_LAT_FLAG + EQUALS_FORM + CENTER_LAT});
        assertEquals(CENTER_LON_VALUE, options.centerLongitude());
        assertEquals(CENTER_LAT_VALUE, options.centerLatitude());
    }

    @Test
    void optionsRejectIncompleteGeographicCenter() {
        assertThrows(
                IllegalArgumentException.class, () -> CliOptions.parse(new String[] {CENTER_LON_FLAG, CENTER_LON}));
        assertThrows(
                IllegalArgumentException.class, () -> CliOptions.parse(new String[] {CENTER_LAT_FLAG, CENTER_LAT}));
    }

    @Test
    void optionsRejectIncompleteMinecraftCenter() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {CENTER_X_FLAG, CENTER_X}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {CENTER_Z_FLAG, CENTER_Z}));
    }

    @Test
    void optionsRejectMixedCenterForms() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {
                    CENTER_LON_FLAG,
                    CENTER_LON,
                    CENTER_LAT_FLAG,
                    CENTER_LAT,
                    CENTER_X_FLAG,
                    CENTER_X,
                    CENTER_Z_FLAG,
                    CENTER_Z
                }));
    }

    @Test
    void optionsRejectOutOfRangeCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {CENTER_LON_FLAG, "181", CENTER_LAT_FLAG, CENTER_LAT}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {CENTER_LON_FLAG, CENTER_LON, CENTER_LAT_FLAG, "91"}));
    }

    @Test
    void openBrowserFlagForcesOpening() {
        assertTrue(CliOptions.parse(new String[] {OPEN_BROWSER_FLAG}).openBrowser());
        assertTrue(TerraPreview.shouldOpenBrowser(CliOptions.parse(new String[] {OPEN_BROWSER_FLAG})));
    }

    @Test
    void noOpenBrowserFlagForcesNotOpening() {
        assertFalse(CliOptions.parse(new String[] {NO_OPEN_BROWSER_FLAG}).openBrowser());
        assertFalse(TerraPreview.shouldOpenBrowser(CliOptions.parse(new String[] {NO_OPEN_BROWSER_FLAG})));
    }

    @Test
    void browserFlagsTakeNoValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {OPEN_BROWSER_FLAG + EQUALS_FORM + "true"}));
        assertThrows(
                IllegalArgumentException.class, () -> CliOptions.parse(new String[] {PORT_FLAG, OPEN_BROWSER_FLAG}));
    }

    @Test
    void lastBrowserFlagWins() {
        assertTrue(CliOptions.parse(new String[] {NO_OPEN_BROWSER_FLAG, OPEN_BROWSER_FLAG})
                .openBrowser());
        assertFalse(CliOptions.parse(new String[] {OPEN_BROWSER_FLAG, NO_OPEN_BROWSER_FLAG})
                .openBrowser());
    }

    @Test
    void optionsRejectNonNumericCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {CENTER_LON_FLAG, "abc", CENTER_LAT_FLAG, CENTER_LAT}));
    }

    @Test
    void webServerServesPreviewPage() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        assertEquals(PreviewWebServer.DEFAULT_PORT, server.port());
        server.start();
        try {
            HttpResponse<String> response = this.get(BASE_URL + "/", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    "text/html; charset=utf-8",
                    response.headers().firstValue(CONTENT_TYPE_HEADER).orElseThrow());
            assertEquals(server.previewPage(), response.body());
            assertTrue(response.body().contains("L.map('map'"));
            assertTrue(response.body().contains("unpkg.com/leaflet@1.9.4"));
        } finally {
            server.stop();
        }
    }

    @Test
    void tileEndpointServesPngImage() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<byte[]> response =
                    this.get(BASE_URL + "/tiles/0/3/-7.png", HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    "image/png",
                    response.headers().firstValue(CONTENT_TYPE_HEADER).orElseThrow());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            assertEquals(STUB_TILE.getWidth(), image.getWidth());
            assertEquals(STUB_TILE.getHeight(), image.getHeight());
            assertEquals(Color.RED.getRGB(), image.getRGB(0, 0));
        } finally {
            server.stop();
        }
    }

    @Test
    void tileEndpointRejectsNonTilePaths() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<String> response =
                    this.get(BASE_URL + "/tiles/0/not-a-tile.png", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void geoEndpointReturnsGeographicCoordinates() throws IOException, InterruptedException {
        Preview preview = new Preview() {
            @Override
            public BufferedImage renderTile(int zoom, int tileX, int tileZ) {
                return STUB_TILE;
            }

            @Override
            public double[] blockToGeo(double x, double z) {
                return new double[] {x / GEO_TEST_SCALE, z / GEO_TEST_SCALE};
            }
        };
        PreviewWebServer server = new PreviewWebServer(preview);
        server.start();
        try {
            HttpResponse<String> response = this.get(BASE_URL + "/geo/12/-34", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    JSON_CONTENT_TYPE,
                    response.headers().firstValue(CONTENT_TYPE_HEADER).orElseThrow());
            assertEquals("{\"longitude\":0.012,\"latitude\":-0.034}", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void geoEndpointRejectsPositionsOutsideTheProjection() throws IOException, InterruptedException {
        Preview preview = new Preview() {
            @Override
            public BufferedImage renderTile(int zoom, int tileX, int tileZ) {
                return STUB_TILE;
            }

            @Override
            public double[] blockToGeo(double x, double z) {
                throw new IllegalArgumentException("Location is outside the projection bounds");
            }
        };
        PreviewWebServer server = new PreviewWebServer(preview);
        server.start();
        try {
            HttpResponse<String> response = this.get(BASE_URL + "/geo/0/0", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void geoEndpointRejectsNonGeoPaths() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<String> response =
                    this.get(BASE_URL + "/geo/not-a-position", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
        } finally {
            server.stop();
        }
    }

    private static final String ERROR_ENDPOINT = "/error";
    private static final int TILE_TEST_ZOOM = 2;
    private static final int TILE_TEST_X = -3;
    private static final int TILE_TEST_Z = 7;

    @Test
    void errorEndpointServesTheLastError() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<String> none = this.get(BASE_URL + ERROR_ENDPOINT, HttpResponse.BodyHandlers.ofString());
            assertEquals("null", none.body());
            server.setError("Could not reload the Terra-- configuration: invalid json");
            HttpResponse<String> error = this.get(BASE_URL + ERROR_ENDPOINT, HttpResponse.BodyHandlers.ofString());
            assertEquals("\"Could not reload the Terra-- configuration: invalid json\"", error.body());
            assertEquals(
                    JSON_CONTENT_TYPE,
                    error.headers().firstValue(CONTENT_TYPE_HEADER).orElseThrow());
            server.setPreview((_, _, _) -> STUB_TILE);
            HttpResponse<String> cleared = this.get(BASE_URL + ERROR_ENDPOINT, HttpResponse.BodyHandlers.ofString());
            assertEquals("null", cleared.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void unavailablePreviewServesBlankWhiteTiles() {
        Preview preview = new UnavailablePreview();
        BufferedImage tile = preview.renderTile(TILE_TEST_ZOOM, TILE_TEST_X, TILE_TEST_Z);
        assertEquals(EarthPreview.TILE_SIZE, tile.getWidth());
        assertEquals(EarthPreview.TILE_SIZE, tile.getHeight());
        assertEquals(Color.WHITE.getRGB(), tile.getRGB(0, 0));
        assertEquals(Color.WHITE.getRGB(), tile.getRGB(tile.getWidth() - 1, tile.getHeight() - 1));
    }

    @Test
    void previewPageLocksZoomAndUsesChunkTiles() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertTrue(page.contains("minZoom: -5"));
        assertTrue(page.contains("maxZoom: 3"));
        assertTrue(page.contains("tileSize: TILE_SIZE"));
        assertTrue(page.contains("'/tiles/' + coords.z + '/' + coords.x + '/' + coords.y + '.png?v=' + tileVersion"));
    }

    @Test
    void previewPageSubstitutesTemplatePlaceholders() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertFalse(Pattern.compile("@[A-Z_]+@").matcher(page).find());
    }

    @Test
    void servedPageListensForServerSentEvents() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertTrue(page.contains("new EventSource('/events')"));
        assertTrue(page.contains("tileLayers.push(layer)"));
        assertTrue(page.contains("map.removeLayer(tileLayers.shift())"));
    }

    @Test
    void servedPageShowsOfflineBanner() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertTrue(page.contains("offline-banner"));
        assertTrue(page.contains("events.onerror"));
        assertTrue(page.contains("events.onopen"));
    }

    @Test
    void servedPageReloadsWhenServerRestarts() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertTrue(page.contains("addEventListener('hello'"));
        assertTrue(page.contains("location.reload()"));
    }

    @Test
    void eventStreamSendsInstanceIdOnConnect() throws IOException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            try (InputStream in = URI.create(BASE_URL + "/events").toURL().openStream();
                    BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                String hello = null;
                while (hello == null && (line = lines.readLine()) != null) {
                    if (line.startsWith(DATA_PREFIX)) {
                        hello = line.substring(DATA_PREFIX.length()).trim();
                    }
                }
                assertFalse(hello == null || hello.isEmpty(), "the event stream did not send an instance identifier");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void eventStreamNotifiesOnPreviewChange() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch reloaded = new CountDownLatch(1);
        AtomicReference<String> event = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (InputStream in = URI.create(BASE_URL + "/events").toURL().openStream();
                    BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                boolean namedEvent = false;
                while ((line = lines.readLine()) != null) {
                    if (line.startsWith("retry:")) {
                        connected.countDown();
                    }
                    if (line.startsWith("event:")) {
                        namedEvent = true;
                        continue;
                    }
                    if (line.startsWith(DATA_PREFIX)) {
                        if (namedEvent) {
                            namedEvent = false;
                            continue;
                        }
                        event.set(line);
                        reloaded.countDown();
                        return;
                    }
                }
            } catch (IOException _) {
                // stream closed when the server stops
            }
        });
        reader.setDaemon(true);
        reader.start();
        try {
            assertTrue(connected.await(TIMEOUT_NANOS, TimeUnit.NANOSECONDS), "event stream did not open");
            server.setPreview((_, _, _) -> STUB_TILE);
            assertTrue(reloaded.await(TIMEOUT_NANOS, TimeUnit.NANOSECONDS), "no event was received");
            assertEquals("data: 1", event.get());
        } finally {
            server.stop();
        }
    }

    @Test
    void defaultCenterChunkIsWithinTheProjectedWorld() {
        assertNotNull(new EarthPreview().defaultCenterChunk());
    }

    @Test
    void webServerBindsToLoopbackOnly() throws IOException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            try (ServerSocket socket = new ServerSocket()) {
                socket.bind(new InetSocketAddress("0.0.0.0", server.port()));
                fail("the web server port was reachable on a non-loopback interface");
            } catch (IOException _) {
                // expected: the port is already bound on the loopback interface only
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void ephemeralPortIsAssignedOnStart() throws IOException, InterruptedException {
        PreviewWebServer server =
                new PreviewWebServer((_, _, _) -> STUB_TILE, new double[] {0, 0}, PreviewWebServer.ANY_PORT);
        assertEquals(PreviewWebServer.ANY_PORT, server.port());
        server.start();
        try {
            assertTrue(server.port() > 0, "the ephemeral port was not assigned");
            HttpResponse<String> response =
                    this.get("http://localhost:" + server.port() + "/", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void locateEndpointReturnsMinecraftBlockCoordinates() throws IOException, InterruptedException {
        Preview preview = new Preview() {
            @Override
            public BufferedImage renderTile(int zoom, int tileX, int tileZ) {
                return STUB_TILE;
            }

            @Override
            public double[] geoToBlock(double longitude, double latitude) {
                return new double[] {longitude * GEO_TEST_SCALE, latitude * GEO_TEST_SCALE};
            }
        };
        PreviewWebServer server = new PreviewWebServer(preview);
        server.start();
        try {
            HttpResponse<String> response =
                    this.get(BASE_URL + "/locate/12.5/-34.25", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    JSON_CONTENT_TYPE,
                    response.headers().firstValue(CONTENT_TYPE_HEADER).orElseThrow());
            assertEquals("{\"x\":12500.0,\"z\":-34250.0}", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void locateEndpointRejectsPositionsOutsideTheProjection() throws IOException, InterruptedException {
        Preview preview = new Preview() {
            @Override
            public BufferedImage renderTile(int zoom, int tileX, int tileZ) {
                return STUB_TILE;
            }

            @Override
            public double[] geoToBlock(double longitude, double latitude) {
                throw new IllegalArgumentException("Location is outside the projection bounds");
            }
        };
        PreviewWebServer server = new PreviewWebServer(preview);
        server.start();
        try {
            HttpResponse<String> response = this.get(BASE_URL + "/locate/0/0", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void locateEndpointRejectsNonLocatePaths() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<String> response =
                    this.get(BASE_URL + "/locate/not-a-position", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void locateEndpointReportsPreviewsWithoutGeographicMapping() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<String> response = this.get(BASE_URL + "/locate/1/1", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void previewPageMentionsTheGeocoder() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertTrue(page.contains("GEOCODER_URL = 'https://photon.komoot.io'"));
        assertTrue(page.contains("GEOCODER_NAME = 'Photon'"));
        assertTrue(page.contains("SEARCH_URL + encodeURIComponent(query)"));
        assertTrue(page.contains("'/locate/' + longitude + '/' + latitude"));
    }

    private <T> HttpResponse<T> get(String url, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        return HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), bodyHandler);
    }
}
