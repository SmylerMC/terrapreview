package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class TerraPreviewTest {

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
                    HttpRequest.newBuilder(URI.create("http://localhost:" + PreviewWebServer.PORT + "/"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
            assertEquals(
                    "text/html; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(PreviewWebServer.wrapInPage("stub <preview>"), response.body());
            assertTrue(response.body().contains("<pre>stub &lt;preview></pre>"));
        } finally {
            server.stop();
        }
    }
}
