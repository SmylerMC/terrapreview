package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The embedded Chromium pane showing the Terrapreview web UI.
 *
 * <p>The browser is created with off-screen rendering so that it behaves like a normal Swing
 * component: it paints inside splitters and hides when its component is hidden, which a
 * windowed native browser does not.
 */
final class PreviewBrowser implements Disposable {

    private final JBCefBrowser browser;

    PreviewBrowser(String url) {
        this.browser = new JBCefBrowserBuilder()
                .setUrl(url)
                .setOffScreenRendering(true)
                .setCreateImmediately(true)
                .build();
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
