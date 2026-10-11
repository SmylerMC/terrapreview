package net.smyler.terrapreview;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Recognizes Terra-- configuration files: the JSON and JSON5 files under a {@code terraminusminus/config} directory.
 */
public final class TerraConfigurationFiles {

    private static final String CONFIG_DIRECTORY = "config";
    private static final String TERRAMINUSMINUS_DIRECTORY = "terraminusminus";
    private static final String JSON_EXTENSION = ".json";
    private static final String JSON5_EXTENSION = ".json5";

    private TerraConfigurationFiles() {}

    /**
     * Returns whether the given path points to a Terra-- configuration file.
     *
     * @param path the path to check, may be null
     * @return true if the path is a JSON or JSON5 file under a terraminusminus configuration directory
     */
    public static boolean isConfigurationPath(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        if (!name.endsWith(JSON_EXTENSION) && !name.endsWith(JSON5_EXTENSION)) {
            return false;
        }
        for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
            if (isConfigDirectory(parent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigDirectory(Path directory) {
        Path terraminusminus = directory.getParent();
        return terraminusminus != null
                && directory.endsWith(CONFIG_DIRECTORY)
                && terraminusminus.endsWith(TERRAMINUSMINUS_DIRECTORY);
    }
}
