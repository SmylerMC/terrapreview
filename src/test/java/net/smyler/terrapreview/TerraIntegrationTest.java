package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

class TerraIntegrationTest {

    @Test
    void userAgentIsTerrapreviewSpecific() throws Exception {
        TerraIntegration.setup();
        Class<?> http = Class.forName("net.buildtheearth.terraminusminus.util.http.Http");
        Object userAgent = http.getMethod("userAgent").invoke(null);
        assertEquals(TerraIntegration.USER_AGENT, userAgent);
    }

    @Test
    void userAgentIdentifiesTerrapreview() {
        assertTrue(TerraIntegration.USER_AGENT.startsWith("terrapreview/"));
    }

    @Test
    void terraLoggerIsConfiguredForInfoLogging() {
        TerraIntegration.setup();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var loggerConfig = context.getConfiguration().getLoggerConfig("terra--");
        assertTrue(loggerConfig.getLevel().isLessSpecificThan(Level.INFO));
    }

    @Test
    void terrapreviewLoggerIsConfiguredForInfoLogging() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var loggerConfig = context.getConfiguration().getLoggerConfig("net.smyler.terrapreview");
        assertTrue(loggerConfig.getLevel().isLessSpecificThan(Level.INFO));
    }
}
