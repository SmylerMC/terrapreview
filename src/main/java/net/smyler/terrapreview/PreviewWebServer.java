package net.smyler.terrapreview;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A very simple web server serving the ASCII earth preview.
 */
public final class PreviewWebServer {

    /**
     * The port the web server listens on.
     */
    public static final int PORT = 8080;

    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <title>TerraPreview</title>
            <style>
            html, body {
                height: 100%;
                margin: 0;
            }
            body {
                display: flex;
                align-items: center;
                justify-content: center;
            }
            pre {
                font-family: monospace;
                font-size: 14px;
                line-height: 1.1;
            }
            </style>
            </head>
            <body>
            <pre>%s</pre>
            </body>
            </html>
            """;

    private final Preview preview;
    private HttpServer server;

    /**
     * Creates a new web server serving the given preview.
     *
     * @param preview the preview to serve
     */
    public PreviewWebServer(Preview preview) {
        this.preview = preview;
    }

    /**
     * Starts the web server on {@link #PORT}.
     *
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);
        this.server.createContext("/", exchange -> {
            String art = this.preview.render(EarthPreview.DEFAULT_LONGITUDE, EarthPreview.DEFAULT_LATITUDE);
            byte[] body = wrapInPage(art).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        this.server.start();
    }

    /**
     * Stops the web server.
     */
    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
    }

    /**
     * Embeds the given ASCII art in a minimal centered HTML page.
     *
     * @param art the ASCII art to embed
     * @return the HTML page content
     */
    public static String wrapInPage(String art) {
        String escaped = art.replace("&", "&amp;").replace("<", "&lt;");
        return PAGE_TEMPLATE.replace("%s", escaped);
    }
}
