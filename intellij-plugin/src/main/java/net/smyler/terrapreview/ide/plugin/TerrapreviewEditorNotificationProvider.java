package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suggests opening the Terrapreview tool window when a Terra-- configuration file is opened.
 */
public final class TerrapreviewEditorNotificationProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        if (!TerraConfigurationFileRecognizer.isConfigurationFile(file)) {
            return null;
        }
        return editor -> this.createPanel(project, editor);
    }

    private EditorNotificationPanel createPanel(Project project, FileEditor editor) {
        EditorNotificationPanel panel = new EditorNotificationPanel(editor);
        panel.setText("Terrapreview can live-preview this Terra-- configuration.");
        panel.createActionLabel("Show preview", () -> this.showPreview(project));
        panel.createActionLabel("Not now", () -> panel.setVisible(false));
        return panel;
    }

    private void showPreview(Project project) {
        ToolWindow toolWindow =
                ToolWindowManager.getInstance(project).getToolWindow(TerrapreviewToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.show(null);
        }
    }
}
