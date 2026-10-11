package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.buildtheearth.terraminusminus.util.http.Disk;
import net.smyler.terrapreview.BlockTextures;
import net.smyler.terrapreview.ConfigWatcher;
import net.smyler.terrapreview.EarthPreview;
import net.smyler.terrapreview.GeneratorSettingsFile;
import net.smyler.terrapreview.MinecraftClientJar;
import net.smyler.terrapreview.Preview;
import net.smyler.terrapreview.PreviewWebServer;
import net.smyler.terrapreview.TerraIntegration;
import net.smyler.terrapreview.UnavailablePreview;

/**
 * Runs the Terrapreview web server and configuration watcher for an open project.
 */
public final class TerrapreviewService implements Disposable {

    private static final Logger LOGGER = Logger.getInstance(TerrapreviewService.class);

    private final BlockTextures textures;
    private final Path configRoot;
    private final PreviewWebServer server;
    private String startupError;
    private final ConfigWatcher watcher;
    private boolean running;

    /**
     * Creates and starts the Terrapreview server for the given project.
     *
     * @param project the project whose configuration directory to watch
     * @throws IOException if the server or watcher cannot be started
     */
    public TerrapreviewService(Project project) throws IOException {
        TerraIntegration.setup();
        this.textures = loadTextures();
        this.configRoot = Files.createDirectories(configRootOf(project));
        setConfigRoot(this.configRoot);
        Preview preview;
        double[] centerChunk;
        try {
            EarthPreview earthPreview = new EarthPreview(GeneratorSettingsFile.tryLoad(this.configRoot), this.textures);
            preview = earthPreview;
            centerChunk = earthPreview.defaultCenterChunk();
            // CHECKSTYLE:OFF: IllegalCatch - an invalid user configuration must not prevent the server from starting
        } catch (IOException | RuntimeException e) {
            LOGGER.error("The Terra-- configuration is invalid, serving an empty preview", e);
            preview = new UnavailablePreview();
            centerChunk = new double[] {0, 0};
            this.startupError = "The Terra-- configuration is invalid: " + e.getMessage();
            // CHECKSTYLE:ON
        }
        this.server = new PreviewWebServer(preview, centerChunk, PreviewWebServer.ANY_PORT);
        this.server.start();
        if (this.startupError != null) {
            this.server.setError(this.startupError);
        }
        this.watcher = this.newWatcher();
        this.watcher.start();
        this.running = true;
    }

    /**
     * Returns the URL of the preview page.
     *
     * @return the preview URL
     */
    public String previewUrl() {
        return "http://localhost:" + this.server.port() + "/";
    }

    /**
     * Reloads the preview whenever a Terra-- configuration file changes, until the given disposable is disposed.
     *
     * @param parent the disposable controlling the lifetime of the subscription
     */
    public void subscribeToConfigurationChanges(Disposable parent) {
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect(parent)
                .subscribe(VirtualFileManager.VFS_CHANGES, new TerrapreviewConfigFileListener(this));
    }

    /**
     * Rebuilds the previewed world from the current configuration files.
     */
    public void reloadPreview() {
        try {
            this.server.setPreview(this.newPreview());
            // CHECKSTYLE:OFF: IllegalCatch - an invalid user configuration must not kill the watcher
        } catch (IOException | RuntimeException e) {
            LOGGER.error("The new configuration is invalid, keeping the previous world", e);
            this.server.setError("Could not reload the Terra-- configuration: " + e.getMessage());
            // CHECKSTYLE:ON
        }
    }

    private EarthPreview newPreview() throws IOException {
        return new EarthPreview(GeneratorSettingsFile.tryLoad(this.configRoot), this.textures);
    }

    private static void setConfigRoot(Path configRoot) {
        try {
            Disk.setConfigRoot(configRoot.toFile());
        } catch (IllegalStateException e) {
            LOGGER.warn("A configuration root was already set, keeping it");
        }
    }

    private static Path configRootOf(Project project) {
        String basePath = project.getBasePath();
        return basePath != null
                ? Path.of(basePath, "terraminusminus", "config")
                : Path.of("terraminusminus", "config");
    }

    private ConfigWatcher newWatcher() {
        try {
            return new ConfigWatcher(Files.createDirectories(this.configRoot), this::reloadPreview);
        } catch (IOException e) {
            LOGGER.error("Failed to watch the configuration directory " + this.configRoot, e);
            try {
                return new ConfigWatcher(Files.createTempDirectory("terrapreview"), () -> {});
            } catch (IOException e2) {
                throw new IllegalStateException("Could not create a fallback watched directory", e2);
            }
        }
    }

    private static BlockTextures loadTextures() {
        Path cache = Path.of(System.getProperty("user.home"), ".terrapreview", "minecraft");
        try {
            MinecraftClientJar clientJar = MinecraftClientJar.ensureDownloaded(cache, null);
            return new BlockTextures(clientJar.path());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load block textures, falling back to flat colors", e);
            return null;
        }
    }

    @Override
    public void dispose() {
        if (this.running) {
            this.running = false;
            this.server.stop();
            try {
                this.watcher.stop();
            } catch (IOException e) {
                LOGGER.warn("Failed to stop the configuration watcher", e);
            }
        }
    }
}
