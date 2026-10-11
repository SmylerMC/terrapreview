package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.jcef.JBCefApp;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The preview half of the split editor of a Terra-- configuration file: an embedded browser
 * showing the live Terrapreview map of the project's configuration.
 */
final class TerrapreviewPreviewEditor extends UserDataHolderBase implements FileEditor {

    private final VirtualFile file;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final TerrapreviewService service;
    private PreviewBrowser browser;
    private JComponent component;

    private TerrapreviewPreviewEditor(VirtualFile file, TerrapreviewService service) {
        this.file = file;
        this.service = service;
    }

    /**
     * Creates the preview editor for the given configuration file, or null when the preview cannot be shown.
     *
     * @param project the project the file belongs to
     * @param file the Terra-- configuration file being previewed
     * @param service the project's Terrapreview service
     * @return a new preview editor, or null when JCEF is unavailable or the server cannot start
     */
    @Nullable
    static TerrapreviewPreviewEditor create(@NotNull Project project, @NotNull VirtualFile file) {
        if (!JBCefApp.isSupported()) {
            return null;
        }
        TerrapreviewService service = TerrapreviewService.getInstance(project);
        return service != null ? new TerrapreviewPreviewEditor(file, service) : null;
    }

    @Override
    public @NotNull JComponent getComponent() {
        if (this.component == null) {
            this.browser = new PreviewBrowser(this.service.previewUrl());
            this.component = this.browser.component();
        }
        return this.component;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return this.getComponent();
    }

    @Override
    public @NotNull String getName() {
        return "Terrapreview";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {}

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return TerraConfigurationFileRecognizer.isConfigurationFile(this.file);
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        this.support.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        this.support.removePropertyChangeListener(listener);
    }

    @Override
    public void dispose() {
        if (this.browser != null) {
            this.browser.dispose();
            this.browser = null;
        }
    }

    @Override
    public @Nullable VirtualFile getFile() {
        return this.file;
    }
}
