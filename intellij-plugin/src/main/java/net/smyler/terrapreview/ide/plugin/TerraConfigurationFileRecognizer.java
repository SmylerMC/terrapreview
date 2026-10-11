package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.vfs.VirtualFile;
import net.smyler.terrapreview.TerraConfigurationFiles;

/**
 * Recognizes Terra-- configuration files among IDE virtual files.
 */
public final class TerraConfigurationFileRecognizer {

    private TerraConfigurationFileRecognizer() {}

    /**
     * Returns whether the given file is a Terra-- configuration file.
     *
     * @param file the file to check, may be null
     * @return true if the file is a JSON or JSON5 file under a terraminusminus configuration directory
     */
    public static boolean isConfigurationFile(VirtualFile file) {
        return file != null && file.isInLocalFileSystem() && TerraConfigurationFiles.isConfigurationPath(file.toNioPath());
    }
}
