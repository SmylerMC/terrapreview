package net.smyler.terrapreview.osmconfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.buildtheearth.terraminusminus.dataset.osm.OSMMapper;
import org.junit.jupiter.api.Test;

/**
 * Checks that the bundled {@code osm.json5} schema accepts Terra--'s own default configuration.
 *
 * <p>The default configuration is read from the terraminusminus jar on the test classpath, the same
 * resource {@link OSMMapper#load() the generator itself} falls back to. Running it through the
 * duplicate-preserving reader and the schema validator guards against schema changes that would
 * make valid configurations, like the shipped default, fail validation in the IDE.</p>
 */
class OsmSchemaValidationTest {

    private static final String DEFAULT_CONFIG_RESOURCE = "/net/buildtheearth/terraminusminus/dataset/osm/osm.json5";

    @Test
    void defaultTerraConfigIsValid() throws IOException {
        DuplicatePreservingJsonReader.Value document;
        try (InputStream stream = OsmSchemaValidationTest.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            assertNotNull(stream, "the default osm.json5 must be on the classpath");
            document = DuplicatePreservingJsonReader.read(reader);
        }
        assertNull(
                OsmJsonSchemaValidator.loadBundled().validate(document),
                "the default osm.json5 must satisfy the schema");
    }

    @Test
    void emptyDocumentIsReported() throws IOException {
        String empty = "{}";
        DuplicatePreservingJsonReader.Value document =
                DuplicatePreservingJsonReader.read(new java.io.StringReader(empty));
        assertNotNull(OsmJsonSchemaValidator.loadBundled().validate(document), "an empty document must not validate");
    }
}
