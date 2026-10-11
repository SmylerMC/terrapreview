package net.smyler.terrapreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Terra-- generator settings file of a project, {@code default_generator_settings.json5} under its configuration directory.
 */
public final class GeneratorSettingsFile {

    private static final Logger LOGGER = LogManager.getLogger(GeneratorSettingsFile.class);

    private static final String FILE_NAME = "default_generator_settings";
    private static final String JSON5_EXTENSION = ".json5";
    private static final String JSON_EXTENSION = ".json";

    private GeneratorSettingsFile() {}

    /**
     * Loads the generator settings of the given configuration directory.
     *
     * <p>The settings are read from {@code default_generator_settings.json5} (or {@code .json}) inside the
     * directory, falling back to the Build The Earth default settings when the file is absent or invalid.
     *
     * @param configRoot the Terra-- configuration directory to load the settings from
     * @return the generator settings of the directory, never null
     */
    public static EarthGeneratorSettings load(Path configRoot) {
        for (String extension : new String[] {JSON5_EXTENSION, JSON_EXTENSION}) {
            Path file = configRoot.resolve(FILE_NAME + extension);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                return EarthGeneratorSettings.parse(Files.readString(file));
                // CHECKSTYLE:OFF: IllegalCatch - the settings file is user-provided, any parse failure must fall back
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Failed to parse {}, falling back to the default settings", file, e);
            }
            // CHECKSTYLE:ON
        }
        return EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    }
}
