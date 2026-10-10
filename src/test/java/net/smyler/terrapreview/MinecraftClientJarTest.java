package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class MinecraftClientJarTest {

    private static final long CLIENT_JAR_SIZE = 41483720L;

    private static final String MANIFEST = """
            {
              "latest": { "release": "26.3", "snapshot": "26.4-snapshot-3" },
              "versions": [
                { "id": "26.4-snapshot-3", "url": "https://example.com/snapshot.json" },
                { "id": "26.3", "url": "https://example.com/release.json" }
              ]
            }
            """;

    @Test
    void latestReleaseIdReadsTheLatestReleaseFromTheManifest() throws IOException {
        assertEquals("26.3", MinecraftClientJar.latestReleaseId(MANIFEST));
    }

    @Test
    void versionUrlFindsTheUrlOfTheGivenVersion() throws IOException {
        assertEquals("https://example.com/release.json", MinecraftClientJar.versionUrl(MANIFEST, "26.3"));
    }

    @Test
    void versionUrlRejectsUnknownVersions() {
        assertThrows(IOException.class, () -> MinecraftClientJar.versionUrl(MANIFEST, "1.0"));
    }

    @Test
    void latestReleaseIdRejectsAManifestWithoutALatestRelease() {
        assertThrows(IOException.class, () -> MinecraftClientJar.latestReleaseId("{}"));
    }

    @Test
    void latestReleaseIdRejectsInvalidJson() {
        assertThrows(IOException.class, () -> MinecraftClientJar.latestReleaseId("not json"));
    }

    @Test
    void clientDownloadParsesTheVersionMetadata() throws IOException {
        String versionJson = """
                {
                  "downloads": {
                    "client": {
                      "url": "https://example.com/client.jar",
                      "sha1": "e877b6a07acd633fb3bb475002175cec036e7b87",
                      "size": 41483720
                    }
                  }
                }
                """;
        var download = MinecraftClientJar.ClientDownload.parse(versionJson);
        assertEquals("https://example.com/client.jar", download.url());
        assertEquals("e877b6a07acd633fb3bb475002175cec036e7b87", download.sha1());
        assertEquals(CLIENT_JAR_SIZE, download.size());
    }

    @Test
    void clientDownloadRejectsMetadataWithoutAClientDownload() {
        assertThrows(IOException.class, () -> MinecraftClientJar.ClientDownload.parse("{}"));
    }

    @Test
    void versionManifestUrlPointsToMojang() {
        assertTrue(MinecraftClientJar.VERSION_MANIFEST_URL.startsWith("https://piston-meta.mojang.com/"));
    }
}
