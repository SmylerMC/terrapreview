package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Reloads the Terrapreview preview when a Terra-- configuration file is saved or modified.
 */
public final class TerrapreviewConfigFileListener implements BulkFileListener {

    private final TerrapreviewService service;

    public TerrapreviewConfigFileListener(@NotNull TerrapreviewService service) {
        this.service = service;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            if (this.isConfigurationChangeEvent(event)) {
                this.service.reloadPreview();
                return;
            }
        }
    }

    private boolean isConfigurationChangeEvent(VFileEvent event) {
        if (event instanceof VFileContentChangeEvent change) {
            return TerraConfigurationFileRecognizer.isConfigurationFile(change.getFile());
        }
        if (event instanceof VFileCreateEvent create) {
            return TerraConfigurationFileRecognizer.isConfigurationFile(create.getFile());
        }
        if (event instanceof VFileDeleteEvent delete) {
            return TerraConfigurationFileRecognizer.isConfigurationFile(delete.getFile());
        }
        return false;
    }
}
