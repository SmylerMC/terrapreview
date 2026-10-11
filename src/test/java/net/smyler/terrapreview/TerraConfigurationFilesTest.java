package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the Terra-- configuration file recognizer.
 */

/**
 * Tests for the Terra-- configuration file recognizer.
 */
class TerraConfigurationFilesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void recognizesConfigurationFiles() throws IOException {
        assertTrue(TerraConfigurationFiles.isConfigurationPath(this.createFile("terraminusminus/config/osm.json5")));
        assertTrue(TerraConfigurationFiles.isConfigurationPath(
                this.createFile("terraminusminus/config/default_generator_settings.json5")));
        assertTrue(TerraConfigurationFiles.isConfigurationPath(
                this.createFile("terraminusminus/config/heights/custom_heights.json")));
        assertTrue(TerraConfigurationFiles.isConfigurationPath(
                this.createFile("project/terraminusminus/config/biome_overrides/overrides.json")));
    }

    @Test
    void rejectsFilesOutsideTheConfigurationDirectory() throws IOException {
        assertFalse(TerraConfigurationFiles.isConfigurationPath(this.createFile("osm.json5")));
        assertFalse(TerraConfigurationFiles.isConfigurationPath(this.createFile("terraminusminus/osm.json5")));
        assertFalse(TerraConfigurationFiles.isConfigurationPath(this.createFile("terraplusplus/config/osm.json5")));
    }

    @Test
    void rejectsNonJsonFiles() throws IOException {
        assertFalse(TerraConfigurationFiles.isConfigurationPath(this.createFile("terraminusminus/config/notes.txt")));
        assertFalse(
                TerraConfigurationFiles.isConfigurationPath(this.createDirectory("terraminusminus/config/heights")));
    }

    @Test
    void rejectsMissingPaths() {
        assertFalse(TerraConfigurationFiles.isConfigurationPath(null));
        assertFalse(TerraConfigurationFiles.isConfigurationPath(
                this.temporaryDirectory.resolve("terraminusminus/config/absent.json5")));
    }

    private Path createFile(String relativePath) throws IOException {
        Path path = this.createDirectory(relativePath).resolve(lastSegment(relativePath));
        Files.writeString(path, "{}");
        return path;
    }

    private static String lastSegment(String relativePath) {
        return relativePath.substring(relativePath.lastIndexOf('/') + 1);
    }

    private Path createDirectory(String relativePath) throws IOException {
        return Files.createDirectories(this.temporaryDirectory.resolve(relativePath));
    }
}
