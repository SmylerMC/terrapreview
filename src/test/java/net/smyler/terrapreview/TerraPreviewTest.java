package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TerraPreviewTest {

    private static final long TIMEOUT_SECONDS = 10L;

    private static final long TIMEOUT_NANOS =
            java.time.Duration.ofSeconds(TIMEOUT_SECONDS).toNanos();

    private static final String STUB_ART = "stub";

    private static final String BASE_URL = "http://localhost:" + PreviewWebServer.PORT;

    @Test
    void greetingIsHelloTerraPreview() {
        assertEquals("Hello, TerraPreview!", TerraPreview.greeting());
    }

    @Test
    void gridIsSquareAndGreaterThanOneChunk() {
        assertTrue(EarthPreview.GRID_SIZE > 1);
        assertEquals(
                EarthPreview.GRID_SIZE * EarthPreview.GRID_SIZE,
                Math.multiplyExact(EarthPreview.GRID_SIZE, EarthPreview.GRID_SIZE));
    }

    @Test
    void webServerServesAsciiPreview() throws IOException, InterruptedException {
        Preview stub = (_, _) -> "stub <preview>";
        PreviewWebServer server = new PreviewWebServer(stub);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    "text/html; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(server.wrapInPage("stub <preview>"), response.body());
            assertTrue(response.body().contains("<pre>stub &lt;preview></pre>"));
        } finally {
            server.stop();
        }
    }

    @Test
    void eventStreamNotifiesOnPreviewChange() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _) -> STUB_ART);
        server.start();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch reloaded = new CountDownLatch(1);
        AtomicReference<String> event = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        URI.create(BASE_URL + "/events").toURL().openConnection();
                try (BufferedReader lines = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
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
                }
            } catch (IOException _) {
                // stream closed when the server stops
            }
        });
        reader.setDaemon(true);
        reader.start();
        try {
            assertTrue(
                    connected.await(TIMEOUT_NANOS, java.util.concurrent.TimeUnit.NANOSECONDS),
                    "event stream did not open");
            server.setPreview((_, _) -> "updated");
            assertTrue(
                    reloaded.await(TIMEOUT_NANOS, java.util.concurrent.TimeUnit.NANOSECONDS), "no event was received");
            assertEquals("data: 1", event.get());
        } finally {
            server.stop();
        }
    }

    @Test
    void servedPageListensForServerSentEvents() {
        PreviewWebServer server = new PreviewWebServer((_, _) -> STUB_ART);
        String page = server.wrapInPage(STUB_ART);
        assertTrue(page.contains("new EventSource('/events')"));
        assertTrue(page.contains("fetch('/preview')"));
        assertTrue(page.contains("document.querySelector('pre').textContent"));
    }

    @Test
    void previewEndpointServesAsciiArt() throws IOException, InterruptedException {
        PreviewWebServer server = new PreviewWebServer((_, _) -> "updated art");
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/preview")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    "text/plain; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("updated art", response.body());
        } finally {
            server.stop();
        }
    }
}
