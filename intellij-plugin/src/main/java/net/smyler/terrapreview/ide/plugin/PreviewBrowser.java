package net.smyler.terrapreview.ide.plugin;

import com.intellij.ui.jcef.JBCefBrowser;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The embedded Chromium pane showing the Terrapreview web UI.
 */
final class PreviewBrowser implements com.intellij.openapi.Disposable {

    private final JBCefBrowser browser;

    PreviewBrowser(String url) {
        this.browser = new JBCefBrowser(url);
    }

    JComponent component() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(this.browser.getComponent(), BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void dispose() {
        this.browser.dispose();
    }
}
