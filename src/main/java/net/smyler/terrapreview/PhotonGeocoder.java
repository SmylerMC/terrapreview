package net.smyler.terrapreview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A geocoder searching place names with the <a href="https://photon.komoot.io">Photon</a> geocoding API.
 */
public class PhotonGeocoder {

    /**
     * The base URL of the public Photon geocoding API.
     */
    public static final String PHOTON_URL = "https://photon.komoot.io";

    private static final String SEARCH_PATH = "/api/";

    private static final String SEARCH_PARAMETERS = "limit=5&osm_tag=place&q=";

    private static final int TIMEOUT_SECONDS = 10;

    private static final int MIN_QUERY_LENGTH = 2;

    private static final Gson GSON = new GsonBuilder().create();

    private final String serviceUrl;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Creates a geocoder using the public Photon API.
     */
    public PhotonGeocoder() {
        this(PHOTON_URL);
    }

    /**
     * Creates a geocoder using the Photon API at the given URL.
     *
     * @param serviceUrl the base URL of the Photon service, without a trailing slash
     */
    public PhotonGeocoder(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * Searches places matching the given text query.
     *
     * <p>Only OpenStreetMap places are searched, to favor named places over individual addresses.
     *
     * @param query the text to search for, such as a city or country name
     * @return the matching places, most relevant first, empty when the query is too short
     * @throws IOException when the Photon service cannot be reached
     * @throws JsonParseException when the Photon service returns an unexpected response
     */
    public List<Place> search(String query) throws IOException {
        if (query == null || query.strip().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String url = this.serviceUrl
                + SEARCH_PATH
                + '?'
                + SEARCH_PARAMETERS
                + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", "TerraPreview")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while searching for places", e);
        }
        try (InputStream body = response.body()) {
            Results results = GSON.fromJson(new InputStreamReader(body, StandardCharsets.UTF_8), Results.class);
            if (results == null || results.features == null) {
                throw new JsonParseException("Missing features in Photon response");
            }
            return results.features.stream()
                    .filter(feature -> feature.hasCoordinates())
                    .map(Place::of)
                    .collect(Collectors.toList());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * The Photon search results, as a GeoJSON feature collection.
     */
    private static final class Results {
        private List<Feature> features;
    }

    private record Feature(Geometry geometry, Properties properties) {

        private boolean hasCoordinates() {
            return this.geometry != null && this.geometry.coordinates != null && this.geometry.coordinates.length == 2;
        }
    }

    private record Geometry(double[] coordinates) {}

    private record Properties(
            String name,
            String city,
            String state,
            String country,
            @SerializedName("osm_value") String osmValue) {}

    /**
     * A place returned by a search, with its geographic location and display name.
     *
     * @param name the short name of the place, typically its own name
     * @param description the larger places it is part of, most specific first
     * @param kind the kind of place, such as city or country
     * @param longitude the longitude of the place, in degrees
     * @param latitude the latitude of the place, in degrees
     */
    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP2", "EI_EXPOSE_REP"},
            justification = "The description list is freshly collected for each place and never shared")
    public record Place(String name, List<String> description, String kind, double longitude, double latitude) {

        private static Place of(Feature feature) {
            double[] coordinates = feature.geometry().coordinates();
            return new Place(
                    feature.properties().name(),
                    Stream.of(
                                    feature.properties().city(),
                                    feature.properties().state(),
                                    feature.properties().country())
                            .filter(part -> part != null && !part.isBlank())
                            .collect(Collectors.toList()),
                    feature.properties().osmValue(),
                    coordinates[0],
                    coordinates[1]);
        }
    }
}
