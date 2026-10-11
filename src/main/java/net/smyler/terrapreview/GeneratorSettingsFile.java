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
        try {
            return tryLoad(configRoot);
        } catch (IOException e) {
            LOGGER.error("Failed to read the generator settings, falling back to the default settings", e);
            // CHECKSTYLE:OFF: IllegalCatch - any parse failure of the user-provided file must fall back
        } catch (RuntimeException e) {
            LOGGER.error("Failed to parse the generator settings, falling back to the default settings", e);
            // CHECKSTYLE:ON
        }
        return EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    }

    /**
     * Loads the generator settings of the given configuration directory, without any fallback.
     *
     * @param configRoot the Terra-- configuration directory to load the settings from
     * @return the generator settings of the directory
     * @throws IOException when the settings file cannot be read
     * @throws IllegalArgumentException when the settings file is invalid
     */
    public static EarthGeneratorSettings tryLoad(Path configRoot) throws IOException {
        for (String extension : new String[] {JSON5_EXTENSION, JSON_EXTENSION}) {
            Path file = configRoot.resolve(FILE_NAME + extension);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                return EarthGeneratorSettings.parse(Files.readString(file));
            } catch (IOException e) {
                throw e;
                // CHECKSTYLE:OFF: IllegalCatch - the settings file is user-provided, any parse failure must be reported
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid generator settings in " + file + ": " + e.getMessage(), e);
            }
            // CHECKSTYLE:ON
        }
        return EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    }
}
