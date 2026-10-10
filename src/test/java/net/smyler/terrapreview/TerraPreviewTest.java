package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TerraPreviewTest {
    private static final long TIMEOUT_SECONDS = 10L;

    private static final long TIMEOUT_NANOS =
            Duration.ofSeconds(TIMEOUT_SECONDS).toNanos();

    private static final BufferedImage STUB_TILE = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);

    private static final String BASE_URL = "http://localhost:" + PreviewWebServer.PORT;

    static {
        STUB_TILE.setRGB(0, 0, Color.RED.getRGB());
    }

    @Test
    void greetingIsHelloTerraPreview() {
        assertEquals("Hello, TerraPreview!", TerraPreview.greeting());
    }

    @Test
    void chunkTilesAreSquareAndChunkSized() {
        assertEquals(EarthPreview.CHUNK_SIZE * EarthPreview.PIXELS_PER_BLOCK, EarthPreview.TILE_SIZE);
    }

    @Test
    void webServerServesPreviewPage() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        server.start();
        try {
            HttpResponse<String> response = this.get(BASE_URL + "/", HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    "text/html; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
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
                    "image/png", response.headers().firstValue("Content-Type").orElseThrow());
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
    void previewPageLocksZoomAndUsesChunkTiles() {
        PreviewWebServer server = new PreviewWebServer((_, _, _) -> STUB_TILE);
        String page = server.previewPage();
        assertTrue(page.contains("minZoom: -2"));
        assertTrue(page.contains("maxZoom: 3"));
        assertTrue(page.contains("tileSize: TILE_SIZE"));
        assertTrue(page.contains("'/tiles/' + coords.z + '/' + coords.x + '/' + coords.y + '.png?v=' + tileVersion"));
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
                while ((line = lines.readLine()) != null) {
                    if (line.startsWith("retry:")) {
                        connected.countDown();
                    }
                    if (line.startsWith("data:")) {
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

    private <T> HttpResponse<T> get(String url, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        return HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), bodyHandler);
    }
}
