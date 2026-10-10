package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a tool window showing the live Terra-- world preview.
 */
public final class TerrapreviewToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        Content content = ContentFactory.getInstance().createContent(null, "", false);
        content.setComponent(new MessagePanel("Starting Terrapreview..."));
        toolWindow.getContentManager().addContent(content);
        ApplicationManager.getApplication().executeOnPooledThread(() -> this.start(project, content));
    }

    private void start(Project project, Content content) {
        TerrapreviewService service;
        try {
            service = new TerrapreviewService(project);
        } catch (IOException e) {
            this.replace(content, new MessagePanel("Terrapreview failed to start: " + e.getMessage()));
            return;
        }
        String url = service.previewUrl();
        if (JBCefApp.isSupported()) {
            PreviewBrowser browser = new PreviewBrowser(url);
            content.setDisposer(new CompositeDisposable(service, browser));
            this.replace(content, browser.component());
        } else {
            content.setDisposer(service);
            this.replace(content, new FallbackPanel(url));
        }
    }

    private void replace(Content content, JComponent component) {
        ApplicationManager.getApplication().invokeLater(() -> {
            content.setComponent(component);
            component.revalidate();
            component.repaint();
        });
    }

    /**
     * Disposes several disposables as one.
     */
    private record CompositeDisposable(Disposable first, Disposable second) implements Disposable {

        @Override
        public void dispose() {
            this.second.dispose();
            this.first.dispose();
        }
    }

    /**
     * A panel showing a single message.
     */
    private static final class MessagePanel extends JPanel {

        private MessagePanel(String message) {
            super(new BorderLayout());
            this.add(new JLabel(message), BorderLayout.CENTER);
        }
    }

    /**
     * A panel offering to open the preview in the system browser when it cannot be embedded.
     */
    private static final class FallbackPanel extends JPanel {

        private FallbackPanel(String url) {
            super(new BorderLayout());
            this.add(new JLabel("Terrapreview is running at " + url), BorderLayout.CENTER);
            JButton open = new JButton("Open in browser");
            open.addActionListener(event -> {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            this.add(open, BorderLayout.SOUTH);
        }
    }
}
