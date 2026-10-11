package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for loading the Terra-- generator settings file.
 */
class GeneratorSettingsFileTest {

    private static final String BTE_SETTINGS = EarthGeneratorSettings.BTE_DEFAULT_SETTINGS;
    private static final String SETTINGS_FILE_NAME = "default_generator_settings.json5";

    @TempDir
    Path temporaryDirectory;

    @Test
    void fallsBackToTheDefaultSettingsWhenTheFileIsAbsent() throws IOException {
        EarthGeneratorSettings settings = GeneratorSettingsFile.load(this.temporaryDirectory);
        assertEquals(EarthGeneratorSettings.parseUncached(BTE_SETTINGS).toString(), settings.toString());
    }

    @Test
    void fallsBackToTheDefaultSettingsWhenTheFileIsInvalid() throws IOException {
        this.write(SETTINGS_FILE_NAME, "{ not json5 ");
        EarthGeneratorSettings settings = GeneratorSettingsFile.load(this.temporaryDirectory);
        assertEquals(EarthGeneratorSettings.parseUncached(BTE_SETTINGS).toString(), settings.toString());
    }

    @Test
    void parsesTheJson5FileOfTheConfigurationDirectory() throws IOException {
        this.write(
                "default_generator_settings.json5",
                "{ \"projection\": { \"scale\": { \"delegate\": { \"equirectangular\": {} },"
                        + " \"x\": 100000.0, \"y\": 100000.0 } },"
                        + " \"useDefaultHeights\": true, \"useDefaultTrees\": true, \"version\": 2 }");
        EarthGeneratorSettings settings = GeneratorSettingsFile.load(this.temporaryDirectory);
        assertNotNull(settings.projection());
        assertTrue(settings.useDefaultHeights());
        assertTrue(settings.useDefaultTreeCover());
    }

    @Test
    void parsesTheJsonFileWhenNoJson5FileExists() throws IOException {
        this.write("default_generator_settings.json", BTE_SETTINGS);
        EarthGeneratorSettings settings = GeneratorSettingsFile.load(this.temporaryDirectory);
        assertEquals(EarthGeneratorSettings.parseUncached(BTE_SETTINGS).toString(), settings.toString());
    }

    @Test
    void tryLoadThrowsOnInvalidSettings() throws IOException {
        this.write(SETTINGS_FILE_NAME, "{ not json5 ");
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                GeneratorSettingsFile.tryLoad(this.temporaryDirectory);
            } catch (IOException e) {
                throw new AssertionError("unexpected IOException", e);
            }
        });
    }

    @Test
    void tryLoadParsesTheSettingsFile() throws IOException {
        this.write("default_generator_settings.json", BTE_SETTINGS);
        EarthGeneratorSettings settings = GeneratorSettingsFile.tryLoad(this.temporaryDirectory);
        assertEquals(EarthGeneratorSettings.parseUncached(BTE_SETTINGS).toString(), settings.toString());
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(this.temporaryDirectory.resolve(fileName), content);
    }
}
