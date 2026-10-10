package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.smyler.terrapreview.BlockTextures;
import net.smyler.terrapreview.ConfigWatcher;
import net.smyler.terrapreview.EarthPreview;
import net.smyler.terrapreview.MinecraftClientJar;
import net.smyler.terrapreview.PreviewWebServer;
import net.smyler.terrapreview.Projections;

/**
 * Runs the Terrapreview web server and configuration watcher for an open project.
 */
public final class TerrapreviewService implements Disposable {

    private static final Logger LOGGER = Logger.getInstance(TerrapreviewService.class);

    private final BlockTextures textures;
    private final PreviewWebServer server;
    private final ConfigWatcher watcher;
    private boolean running;

    /**
     * Creates and starts the Terrapreview server for the given project.
     *
     * @param project the project whose configuration directory to watch
     * @throws IOException if the server or watcher cannot be started
     */
    public TerrapreviewService(Project project) throws IOException {
        this.textures = loadTextures();
        EarthPreview preview = this.newPreview();
        this.server = new PreviewWebServer(preview, preview.defaultCenterChunk(), PreviewWebServer.ANY_PORT);
        this.server.start();
        this.watcher = this.newWatcher(project);
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

    private EarthPreview newPreview() {
        return new EarthPreview(Projections.settings(Projections.BTE), this.textures);
    }

    private ConfigWatcher newWatcher(Project project) {
        String basePath = project.getBasePath();
        Path configRoot = basePath != null
                ? Path.of(basePath, "terraminusminus", "config")
                : Path.of("terraminusminus", "config");
        try {
            return new ConfigWatcher(Files.createDirectories(configRoot), () -> {
                LOGGER.info("Configuration change detected, updating the rendering engine...");
                this.server.setPreview(this.newPreview());
            });
        } catch (IOException e) {
            LOGGER.error("Failed to watch the configuration directory " + configRoot, e);
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
