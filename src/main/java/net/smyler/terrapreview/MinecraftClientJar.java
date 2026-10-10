package net.smyler.terrapreview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * The official Minecraft client JAR downloaded from Mojang's servers.
 *
 * @param path the local path of the client JAR
 * @param version the Minecraft version the JAR corresponds to
 */
public record MinecraftClientJar(Path path, String version) {

    /**
     * Mojang's official version manifest, listing all published Minecraft versions.
     */
    public static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10L);
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String SHA1_ALGORITHM = "SHA-1";
    private static final String DOWNLOADS_FIELD = "downloads";
    private static final String CLIENT_FIELD = "client";

    /**
     * Makes sure the Minecraft client JAR of the given version is present in the given cache directory, downloading
     * it from Mojang's official servers if needed, and verifies it against the published checksum.
     *
     * @param cacheDirectory the directory to cache the client JAR in, created if absent
     * @param version the Minecraft version to load, for example {@code 26.3}, or {@code null} for the latest release
     * @return the downloaded or already cached client JAR
     * @throws IOException if the JAR cannot be downloaded, verified, or written to disk
     */
    public static MinecraftClientJar ensureDownloaded(Path cacheDirectory, String version) throws IOException {
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            String manifest = httpGetBody(client, VERSION_MANIFEST_URL);
            String resolvedVersion = version == null ? latestReleaseId(manifest) : version;
            String versionJson = httpGetBody(client, versionUrl(manifest, resolvedVersion));
            ClientDownload download = ClientDownload.parse(versionJson);
            Path target = Files.createDirectories(cacheDirectory).resolve("client-" + resolvedVersion + ".jar");
            if (Files.exists(target) && Files.size(target) == download.size()) {
                return new MinecraftClientJar(target, resolvedVersion);
            }
            downloadTo(client, download, target, cacheDirectory);
            return new MinecraftClientJar(target, resolvedVersion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading the Minecraft client JAR", e);
        }
    }

    private static void downloadTo(HttpClient client, ClientDownload download, Path target, Path directory)
            throws IOException, InterruptedException {
        Path temporary = Files.createTempFile(directory, "client", ".jar.part");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(download.url()))
                    .header(USER_AGENT_HEADER, TerraIntegration.USER_AGENT)
                    .timeout(REQUEST_TIMEOUT)
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            String sha1 = sha1Of(temporary);
            if (!sha1.equalsIgnoreCase(download.sha1())) {
                throw new IOException(
                        "Minecraft client JAR checksum mismatch: expected " + download.sha1() + ", got " + sha1);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String httpGetBody(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header(USER_AGENT_HEADER, TerraIntegration.USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String sha1Of(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA1_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(SHA1_ALGORITHM + " is not available", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[Short.MAX_VALUE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Finds the identifier of the latest Minecraft release in the given version manifest.
     *
     * @param manifestJson the contents of Mojang's version manifest
     * @return the identifier of the latest release, for example {@code 26.3}
     * @throws IOException if the manifest does not contain a latest release
     */
    static String latestReleaseId(String manifestJson) throws IOException {
        JsonObject latest = memberObject(parseObject(manifestJson), "latest");
        String release = stringOrNull(latest, "release");
        if (release == null) {
            throw new IOException("Version manifest has no latest release");
        }
        return release;
    }

    /**
     * Finds the metadata URL of a Minecraft version in the given version manifest.
     *
     * @param manifestJson the contents of Mojang's version manifest
     * @param version the identifier of the version to look up
     * @return the URL of the version's metadata document
     * @throws IOException if the version is absent from the manifest
     */
    static String versionUrl(String manifestJson, String version) throws IOException {
        JsonArray versions = parseObject(manifestJson).getAsJsonArray("versions");
        if (versions == null) {
            throw new IOException("Version " + version + " is absent from the version manifest");
        }
        for (JsonElement element : versions) {
            JsonObject entry = element.getAsJsonObject();
            if (version.equals(stringOrNull(entry, "id"))) {
                String url = stringOrNull(entry, "url");
                if (url == null) {
                    break;
                }
                return url;
            }
        }
        throw new IOException("Version " + version + " is absent from the version manifest");
    }

    private static JsonObject parseObject(String json) throws IOException {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Failed to parse JSON document", e);
        }
    }

    private static JsonObject memberObject(JsonObject object, String member) throws IOException {
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("JSON document has no " + member + " object");
        }
        return element.getAsJsonObject();
    }

    private static String stringOrNull(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    /**
     * The client JAR download of a Minecraft version.
     *
     * @param url the URL the client JAR can be downloaded from
     * @param sha1 the expected SHA-1 checksum of the client JAR
     * @param size the expected size of the client JAR, in bytes
     */
    record ClientDownload(String url, String sha1, long size) {

        /**
         * Extracts the client JAR download information from a version metadata document.
         *
         * @param versionJson the contents of a Mojang version metadata document
         * @return the client JAR download
         * @throws IOException if the document has no client download
         */
        static ClientDownload parse(String versionJson) throws IOException {
            JsonObject downloads = memberObject(parseObject(versionJson), DOWNLOADS_FIELD);
            JsonObject client = memberObject(downloads, CLIENT_FIELD);
            String url = stringOrNull(client, "url");
            String sha1 = stringOrNull(client, "sha1");
            JsonElement size = client.get("size");
            if (url == null || sha1 == null || size == null || !size.isJsonPrimitive()) {
                throw new IOException("Version metadata has no client JAR download");
            }
            return new ClientDownload(url, sha1, size.getAsLong());
        }
    }
}
