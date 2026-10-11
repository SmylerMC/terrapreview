package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the Terrapreview preview half of the split editor of a Terra-- configuration file.
 */
final class TerrapreviewPreviewFileEditorProvider implements FileEditorProvider, DumbAware {

    private static final String EDITOR_TYPE_ID = "terrapreview.preview";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return TerraConfigurationFileRecognizer.isConfigurationFile(file);
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        TerrapreviewPreviewEditor editor = TerrapreviewPreviewEditor.create(project, file);
        if (editor != null) {
            return editor;
        }
        throw new IllegalStateException("Terrapreview preview is not available for " + file.getName());
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.NONE;
    }

    @Override
    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {}
}
