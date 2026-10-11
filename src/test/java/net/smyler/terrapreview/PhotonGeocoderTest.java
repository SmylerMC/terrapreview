package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhotonGeocoderTest {

    private static final String PARIS_FEATURE = """
            {
                "geometry": {"coordinates": [2.3522, 48.8566]},
                "properties": {
                    "name": "Paris",
                    "city": "Paris",
                    "state": "Île-de-France",
                    "country": "France",
                    "osm_key": "place",
                    "osm_value": "city"
                }
            }""";

    private static final String LONDON_FEATURE = """
            {
                "geometry": {"coordinates": [-0.1276, 51.5072]},
                "properties": {
                    "name": "London",
                    "state": "England",
                    "country": "United Kingdom",
                    "osm_value": "city"
                }
            }""";

    private static final String INVALID_FEATURE = """
            {"geometry": {"coordinates": [1.0]}, "properties": {"name": "Broken"}}""";

    private static final double PARIS_LONGITUDE = 2.3522D;

    private static final double PARIS_LATITUDE = 48.8566D;

    private static final double LONDON_LONGITUDE = -0.1276D;

    private static final double LONDON_LATITUDE = 51.5072D;

    private static final double COORDINATE_TOLERANCE = 0.000001D;

    private static final String PARIS_QUERY = "paris";

    private static final String PARIS_NAME = "Paris";

    private static final int OK_STATUS = 200;

    private static final int UNAVAILABLE_STATUS = 500;

    private HttpServer server;

    private String lastQuery;

    private String responseBody;

    private int responseStatus;

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/", this::serve);
        this.server.start();
        this.responseBody = "{}";
        this.responseStatus = OK_STATUS;
    }

    @AfterEach
    void stopServer() {
        this.server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        this.lastQuery = exchange.getRequestURI().getQuery();
        byte[] body = this.responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(this.responseStatus, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String url() {
        return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/api/";
    }

    private static String collection(String... features) {
        return "{\"features\":[" + String.join(",", features) + "]}";
    }

    @Test
    void searchReturnsPlacesWithTheirDescriptions() throws IOException {
        this.responseBody = collection(PARIS_FEATURE, LONDON_FEATURE);
        List<PhotonGeocoder.Place> places = new PhotonGeocoder(this.url()).search(PARIS_QUERY);
        assertEquals(2, places.size());
        PhotonGeocoder.Place paris = places.getFirst();
        assertEquals(PARIS_NAME, paris.name());
        assertEquals(List.of("Paris", "Île-de-France", "France"), paris.description());
        assertEquals("city", paris.kind());
        assertEquals(PARIS_LONGITUDE, paris.longitude(), COORDINATE_TOLERANCE);
        assertEquals(PARIS_LATITUDE, paris.latitude(), COORDINATE_TOLERANCE);
        PhotonGeocoder.Place london = places.getLast();
        assertEquals("London", london.name());
        assertEquals(List.of("England", "United Kingdom"), london.description());
        assertEquals(LONDON_LONGITUDE, london.longitude(), COORDINATE_TOLERANCE);
        assertEquals(LONDON_LATITUDE, london.latitude(), COORDINATE_TOLERANCE);
    }

    @Test
    void searchIgnoresFeaturesWithoutUsableCoordinates() throws IOException {
        this.responseBody = collection(INVALID_FEATURE, PARIS_FEATURE);
        List<PhotonGeocoder.Place> places = new PhotonGeocoder(this.url()).search(PARIS_QUERY);
        assertEquals(1, places.size());
        assertEquals(PARIS_NAME, places.getFirst().name());
    }

    @Test
    void searchQueriesThePhotonApiWithTheEncodedQuery() throws IOException {
        this.responseBody = collection();
        new PhotonGeocoder(this.url()).search("new york");
        String expected = "limit=5&osm_tag=place&q=" + URLEncoder.encode("new york", StandardCharsets.UTF_8);
        assertEquals(expected, this.lastQuery);
    }

    @Test
    void shortQueriesAreNotSearched() throws IOException {
        assertEquals(List.of(), new PhotonGeocoder(this.url()).search("p"));
        assertEquals(List.of(), new PhotonGeocoder(this.url()).search(" "));
        assertEquals(List.of(), new PhotonGeocoder(this.url()).search(null));
        assertEquals(null, this.lastQuery);
    }

    @Test
    void searchesAreTrimmed() throws IOException {
        this.responseBody = collection();
        new PhotonGeocoder(this.url()).search("  " + PARIS_QUERY + "  ");
        assertTrue(this.lastQuery.endsWith("q=paris"), "unexpected query: " + this.lastQuery);
    }

    @Test
    void searchFailsWhenTheServiceIsUnavailable() {
        this.responseStatus = UNAVAILABLE_STATUS;
        this.responseBody = "try again later";
        assertThrows(JsonParseException.class, () -> new PhotonGeocoder(this.url()).search(PARIS_QUERY));
    }

    @Test
    void searchFailsWhenTheResponseIsMissingFeatures() {
        this.responseBody = "{}";
        assertThrows(JsonParseException.class, () -> new PhotonGeocoder(this.url()).search(PARIS_QUERY));
    }

    @Test
    void searchFailsOnAnUnexpectedResponse() {
        this.responseBody = "<html>not json</html>";
        assertThrows(JsonParseException.class, () -> new PhotonGeocoder(this.url()).search(PARIS_QUERY));
    }
}
