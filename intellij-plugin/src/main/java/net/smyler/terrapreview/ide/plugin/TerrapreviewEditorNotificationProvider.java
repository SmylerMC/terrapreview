package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suggests opening the Terrapreview split preview when a Terra-- configuration file is opened.
 */
public final class TerrapreviewEditorNotificationProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        if (!TerraConfigurationFileRecognizer.isConfigurationFile(file)) {
            return null;
        }
        return editor -> this.createPanel(editor);
    }

    private EditorNotificationPanel createPanel(FileEditor editor) {
        EditorNotificationPanel panel = new EditorNotificationPanel(editor);
        panel.setText("Terrapreview can live-preview this Terra-- configuration.");
        panel.createActionLabel("Show preview", () -> this.showPreview(editor));
        panel.createActionLabel("Not now", () -> panel.setVisible(false));
        return panel;
    }

    private void showPreview(FileEditor editor) {
        if (editor instanceof TextEditorWithPreview split) {
            split.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW);
        }
    }
}
