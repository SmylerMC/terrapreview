package net.smyler.terrapreview;

import net.buildtheearth.terraminusminus.TerraMinusMinus;
import net.buildtheearth.terraminusminus.util.http.Http;

/**
 * Integration with the terraminusminus world generation engine.
 */
public final class TerraIntegration {
    /**
     * The name of this tool, used to identify it to remote servers.
     */
    public static final String NAME = "terrapreview";

    /**
     * The version of this tool, kept in sync with the project version.
     */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    /**
     * The user agent sent by terraminusminus HTTP requests on behalf of this tool.
     */
    public static final String USER_AGENT = NAME + "/" + VERSION;

    private TerraIntegration() {}

    /**
     * Configures terraminusminus for use by this tool: dedicated user agent and HTTP request logging through log4j.
     */
    public static void setup() {
        Http.userAgent(USER_AGENT);
        TerraMinusMinus.LOGGER.info("Terra-- configured with user agent: {}", USER_AGENT);
    }
}
