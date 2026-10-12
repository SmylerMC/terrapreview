package net.smyler.terrapreview.osmconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Detailed checks of the bundled {@code osm.json5} schema against the Terra-- parsing implementation.
 *
 * <p>Every typed union in the format is registered by a parser with a fixed set of type names
 * (LineParser, PolygonParser, MatchParser, DValueParser, DrawFunctionParser). The schema must offer
 * exactly those names as object properties, because IntelliJ derives its key suggestions from them.
 * Each union member's value holds exactly the fields the corresponding Terra-- parser accepts.
 * These tests pin both sides, fragment by fragment, against the schema definitions.</p>
 */
class OsmSchemaTest {

    private static final String ADD = "+";
    private static final String MULTIPLY = "*";
    private static final String FLOOR_DIV = "floor_div";
    private static final String MAX = "max";

    private static final String ALL = "all";
    private static final String ANY = "any";
    private static final String CONDITION = "condition";
    private static final String CONVERT = "convert";
    private static final String FIRST = "first";
    private static final String NOTHING = "nothing";
    private static final String NARROW = "narrow";
    private static final String SHARP = "sharp";
    private static final String WIDE = "wide";
    private static final String DISTANCE = "distance";
    private static final String FILL = "fill";
    private static final String LINE = "line";
    private static final String POLYGON = "polygon";

    private static final String AND = "and";
    private static final String OR = "or";
    private static final String NOT = "not";
    private static final String TAG = "tag";
    private static final String ID = "id";
    private static final String INTERSECTS = "intersects";

    private static final String CONSTANT = "constant";
    private static final String BLOCK = "block";
    private static final String NO_TREES = "no_trees";
    private static final String OCEAN = "ocean";
    private static final String WATER = "water";
    private static final String WEIGHT_ADD = "weight_add";
    private static final String WEIGHT_CLAMP = "weight_clamp";
    private static final String WEIGHT_GREATER_THAN = "weight_greater_than";
    private static final String WEIGHT_LESS_THAN = "weight_less_than";

    private static OsmJsonSchemaValidator validator;
    private static Map<String, Object> schemaDefs;

    @BeforeAll
    static void loadSchema() throws IOException {
        validator = OsmJsonSchemaValidator.loadBundled();
        Gson gson = new Gson();
        try (var stream = OsmSchemaTest.class.getResourceAsStream("osm-schema.json")) {
            assertNotNull(stream, "the schema must be bundled next to this class");
            JsonObject root =
                    gson.fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            schemaDefs = gson.fromJson(root.get("$defs"), Map.class);
        }
    }

    // The fixtures below intentionally repeat JSON fragments and definition names; the format
    // itself repeats keys, and each union is exercised against its own definition.
    // CHECKSTYLE:OFF: MultipleStringLiterals
    private static String fragment(String json, String def) throws IOException {
        return validator.validateFragment(DuplicatePreservingJsonReader.read(new StringReader(json)), def);
    }

    private static void assertValid(String json, String def) throws IOException {
        assertNull(fragment(json, def), "expected valid, got: " + fragment(json, def));
    }

    private static void assertInvalid(String json, String def) throws IOException {
        assertNotNull(fragment(json, def), "expected invalid, but the schema accepted the fragment");
    }

    private static Set<String> propertiesOf(String def) {
        Object node = schemaDefs.get(def);
        assertNotNull(node, "the schema must define " + def);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) ((Map<String, Object>) node).get("properties");
        assertNotNull(properties, def + " must declare named properties: they drive IDE key suggestions");
        return properties.keySet();
    }

    private static String drawAndLayer() {
        return "\"draw\": {\"block\": {\"id\": \"minecraft:bricks\"}}, \"layer\": {\"tag\": {\"key\": \"layer\", \"fallback\": 0}}";
    }

    private static String condition(String matchJson, String emitJson) {
        return "{\"match\": " + matchJson + ", \"emit\": " + emitJson + "}";
    }

    // ------------------------------------------------------------------
    // The suggestion lists must match the Terra-- parser registrations.
    // ------------------------------------------------------------------

    @Test
    void lineMapperOffersExactlyTheLineParserTypes() {
        assertEquals(Set.of(ALL, ANY, CONDITION, FIRST, NOTHING, NARROW, SHARP, WIDE), propertiesOf("lineMapper"));
    }

    @Test
    void polygonMapperOffersExactlyThePolygonParserTypes() {
        assertEquals(
                Set.of(ALL, ANY, CONDITION, CONVERT, FIRST, NOTHING, DISTANCE, FILL), propertiesOf("polygonMapper"));
    }

    @Test
    void matchConditionOffersExactlyTheMatchParserTypes() {
        assertEquals(Set.of(AND, OR, NOT, TAG, ID, INTERSECTS), propertiesOf("matchCondition"));
    }

    @Test
    void dValueOffersExactlyTheDValueParserTypes() {
        assertEquals(Set.of(CONSTANT, TAG, ADD, "-", MULTIPLY, "/", FLOOR_DIV, "min", MAX), propertiesOf("dValue"));
    }

    @Test
    void binaryOperandsOfferExactlyTheDValueParserTypes() {
        assertEquals(
                Set.of(CONSTANT, TAG, ADD, "-", MULTIPLY, "/", FLOOR_DIV, "min", MAX), propertiesOf("binaryOperands"));
    }

    @Test
    void drawFunctionOffersExactlyTheDrawFunctionParserTypes() {
        assertEquals(
                Set.of(
                        ALL,
                        BLOCK,
                        NO_TREES,
                        OCEAN,
                        WATER,
                        WEIGHT_ADD,
                        WEIGHT_CLAMP,
                        WEIGHT_GREATER_THAN,
                        WEIGHT_LESS_THAN),
                propertiesOf("drawFunction"));
    }

    @Test
    void rootOffersOnlyLineAndPolygon() throws IOException {
        String root = "{\"line\": {\"nothing\": {}}, \"polygon\": {\"nothing\": {}}}";
        assertNull(validator.validate(DuplicatePreservingJsonReader.read(new StringReader(root))));
        assertNotNull(validator.validate(
                DuplicatePreservingJsonReader.read(new StringReader("{\"line\": {\"nothing\": {}}}"))));
        assertNotNull(validator.validate(DuplicatePreservingJsonReader.read(
                new StringReader("{\"line\": {\"nothing\": {}}, \"polygon\": {\"nothing\": {}}, \"points\": {}}"))));
    }

    // ------------------------------------------------------------------
    // Mapper payloads: exactly the fields the Terra-- parsers accept.
    // ------------------------------------------------------------------

    @Test
    void nothingTakesNoFields() throws IOException {
        assertValid("{\"nothing\": {}}", "lineMapper");
        assertValid("{\"nothing\": {}}", "polygonMapper");
        assertInvalid("{\"nothing\": {\"anything\": 1}}", "lineMapper");
    }

    @Test
    void narrowSharpAndFillTakeDrawAndLayer() throws IOException {
        for (String type : List.of(NARROW, SHARP)) {
            assertValid("{\"" + type + "\": {" + drawAndLayer() + "}}", "lineMapper");
            assertInvalid("{\"" + type + "\": {\"draw\": {\"nothing\": {}}}}", "lineMapper");
            assertInvalid("{\"" + type + "\": {" + drawAndLayer() + ", \"radius\": {\"constant\": 1}}}", "lineMapper");
        }
        assertValid("{\"" + FILL + "\": {" + drawAndLayer() + "}}", "polygonMapper");
        assertInvalid("{\"" + FILL + "\": {\"draw\": {\"nothing\": {}}}}", "polygonMapper");
        assertInvalid("{\"" + FILL + "\": {" + drawAndLayer() + ", \"maxDist\": 3}}", "polygonMapper");
    }

    @Test
    void wideTakesDrawLayerAndRadius() throws IOException {
        assertValid("{\"" + WIDE + "\": {" + drawAndLayer() + ", \"radius\": {\"constant\": 1}}}", "lineMapper");
        assertInvalid("{\"" + WIDE + "\": {" + drawAndLayer() + "}}", "lineMapper");
        assertInvalid(
                "{\"" + WIDE + "\": {\"draw\": {\"nothing\": {}}, \"layer\": {\"constant\": 0}}}", "polygonMapper");
    }

    @Test
    void distanceTakesDrawLayerAndOptionalMaxDist() throws IOException {
        assertValid("{\"" + DISTANCE + "\": {" + drawAndLayer() + "}}", "polygonMapper");
        assertValid("{\"" + DISTANCE + "\": {" + drawAndLayer() + ", \"maxDist\": 4}}", "polygonMapper");
        assertInvalid("{\"" + DISTANCE + "\": {" + drawAndLayer() + ", \"maxDist\": 1.5}}", "polygonMapper");
        assertInvalid(
                "{\"" + DISTANCE + "\": {" + drawAndLayer() + ", \"radius\": {\"constant\": 1}}}", "polygonMapper");
    }

    @Test
    void convertTakesExactlyALineMapper() throws IOException {
        assertValid(
                "{\"" + CONVERT + "\": {\"" + LINE + "\": {\"" + SHARP + "\": {" + drawAndLayer() + "}}}}",
                "polygonMapper");
        assertInvalid("{\"" + CONVERT + "\": {}}", "polygonMapper");
        assertInvalid(
                "{\"" + CONVERT + "\": {\"" + LINE + "\": {\"" + FILL + "\": {" + drawAndLayer() + "}}}}",
                "polygonMapper");
        assertInvalid(
                "{\"" + CONVERT + "\": {\"" + POLYGON + "\": {\"" + FILL + "\": {" + drawAndLayer() + "}}}}",
                "polygonMapper");
    }

    @Test
    void conditionRequiresMatchAndEmitOfItsOwnKind() throws IOException {
        String tunnelMatch = "{\"tag\": {\"tunnel\": null}}";
        String nothing = "{\"nothing\": {}}";
        assertValid(condition(tunnelMatch, nothing), "lineCondition");
        assertValid(condition(tunnelMatch, nothing), "polygonCondition");
        assertInvalid("{\"match\": " + tunnelMatch + "}", "lineCondition");
        assertInvalid("{\"emit\": " + nothing + "}", "polygonCondition");
        // a line condition cannot emit a polygon mapper and vice versa
        assertInvalid(condition(tunnelMatch, "{\"fill\": {" + drawAndLayer() + "}}"), "lineCondition");
        assertInvalid(condition(tunnelMatch, "{\"narrow\": {" + drawAndLayer() + "}}"), "polygonCondition");
    }

    @Test
    void allAnyAndFirstHoldRepeatedTypedMembers() throws IOException {
        String chain = "{"
                + "\"" + CONDITION + "\": {\"match\": {\"tag\": {\"tunnel\": null}}, \"emit\": {\"nothing\": {}}}, "
                + "\"" + CONDITION + "\": {\"match\": {\"tag\": {\"highway\": \"motorway\"}}, \"emit\": {\"wide\": "
                + wideFields() + "}}, "
                + "\"" + NARROW + "\": {" + drawAndLayer() + "}}";
        assertValid("{\"" + FIRST + "\": " + chain + "}", "lineMapper");
        assertValid("{\"" + ALL + "\": {\"" + NOTHING + "\": {}, \"" + NOTHING + "\": {}}}", "lineMapper");
        assertValid("{\"" + ANY + "\": {\"" + NOTHING + "\": {}, \"" + NOTHING + "\": {}}}", "polygonMapper");
        assertInvalid("{\"" + FIRST + "\": {}}", "lineMapper");
        assertInvalid("{\"" + FIRST + "\": {\"wat\": {}}}", "lineMapper");
        assertInvalid("{\"" + ALL + "\": {\"" + NARROW + "\": {\"layer\": {\"constant\": 0}}}}", "lineMapper");
        assertInvalid("{\"" + ANY + "\": {\"" + FILL + "\": {" + drawAndLayer() + "}}}", "lineMapper");
    }

    // ------------------------------------------------------------------
    // Match conditions.
    // ------------------------------------------------------------------

    private static String wideFields() {
        return "{" + drawAndLayer() + ", \"radius\": {\"constant\": 1}}";
    }

    @Test
    void tagMatchAcceptsNullStringOrStringArray() throws IOException {
        assertValid("{\"tag\": {\"tunnel\": null}}", "matchCondition");
        assertValid("{\"tag\": {\"highway\": \"motorway\"}}", "matchCondition");
        assertValid("{\"tag\": {\"highway\": [\"motorway\", \"trunk\"]}}", "matchCondition");
        assertInvalid("{\"tag\": {}}", "matchCondition");
        assertInvalid("{\"tag\": {\"highway\": 3}}", "matchCondition");
        assertInvalid("{\"tag\": {\"highway\": []}}", "matchCondition");
        assertInvalid("{\"tag\": {\"highway\": null, \"railway\": null}}", "matchCondition");
        assertInvalid("{\"tag\": {\"highway\": [\"motorway\", 2]}}", "matchCondition");
    }

    @Test
    void idMatchAcceptsStringOrStringArray() throws IOException {
        assertValid("{\"id\": \"way/42\"}", "matchCondition");
        assertValid("{\"id\": [\"way/42\", \"way/43\"]}", "matchCondition");
        assertInvalid("{\"id\": 42}", "matchCondition");
        assertInvalid("{\"id\": []}", "matchCondition");
    }

    @Test
    void intersectsRequiresTheFourBounds() throws IOException {
        String bounds = "{\"minX\": 45.637, \"maxX\": 56.536, \"minZ\": 34.597, \"maxZ\": 47.695}";
        assertValid("{\"" + INTERSECTS + "\": " + bounds + "}", "matchCondition");
        assertInvalid("{\"" + INTERSECTS + "\": {\"minX\": 0, \"maxX\": 1, \"minZ\": 0}}", "matchCondition");
        assertInvalid(
                "{\"" + INTERSECTS + "\": {\"minX\": 0, \"maxX\": 1, \"minZ\": 0, \"maxZ\": 1, \"extra\": 0}}",
                "matchCondition");
        assertInvalid(
                "{\"" + INTERSECTS + "\": {\"minX\": 0, \"maxX\": 1, \"minZ\": 0, \"maxZ\": \"1\"}}", "matchCondition");
    }

    @Test
    void andOrNotCombineConditions() throws IOException {
        assertValid(
                "{\"and\": {\"tag\": {\"tunnel\": null}, \"not\": {\"tag\": {\"tunnel\": \"no\"}}}}", "matchCondition");
        assertValid(
                "{\"or\": {\"tag\": {\"water\": null}, \"tag\": {\"natural\": \"water\"}, \"tag\": {\"waterway\": \"riverbank\"}}}",
                "matchCondition");
        assertValid("{\"not\": {\"tag\": {\"tunnel\": \"no\"}}}", "matchCondition");
        assertValid("{\"not\": {\"and\": {\"tag\": {\"tunnel\": null}}}}", "matchCondition");
        assertInvalid("{\"and\": {}}", "matchCondition");
        assertInvalid("{\"or\": {\"wide\": {}}}", "matchCondition");
        assertInvalid("{\"not\": {}}", "matchCondition");
        assertInvalid("{\"not\": 3}", "matchCondition");
    }

    // ------------------------------------------------------------------
    // DValues.
    // ------------------------------------------------------------------

    @Test
    void constantDValueIsANumber() throws IOException {
        assertValid("{\"constant\": 6}", "dValue");
        assertValid("{\"constant\": 1.5}", "dValue");
        assertInvalid("{\"constant\": \"six\"}", "dValue");
    }

    @Test
    void tagDValueRequiresKeyAndFallback() throws IOException {
        assertValid("{\"tag\": {\"key\": \"lanes\", \"fallback\": 2}}", "dValue");
        assertInvalid("{\"tag\": {\"key\": \"lanes\"}}", "dValue");
        assertInvalid("{\"tag\": {\"key\": \"lanes\", \"fallback\": \"two\"}}", "dValue");
        assertInvalid("{\"tag\": {\"key\": \"lanes\", \"fallback\": 2, \"extra\": 0}}", "dValue");
    }

    @Test
    void binaryOperatorsTakeExactlyTwoOperands() throws IOException {
        assertValid("{\"" + ADD + "\": {\"constant\": 1, \"constant\": 2}}", "dValue");
        assertValid(
                "{\"" + FLOOR_DIV + "\": {\"" + MULTIPLY
                        + "\": {\"tag\": {\"key\": \"lanes\", \"fallback\": 2}, \"constant\": 3}, \"constant\": 2}}",
                "dValue");
        assertValid("{\"" + MAX + "\": {\"tag\": {\"key\": \"lanes\", \"fallback\": 2}, \"constant\": 2}}", "dValue");
        assertValid(
                "{\"" + ADD
                        + "\": {\"tag\": {\"key\": \"a\", \"fallback\": 1}, \"tag\": {\"key\": \"b\", \"fallback\": 2}}}",
                "dValue");
        assertInvalid("{\"" + ADD + "\": {\"constant\": 1}}", "dValue");
        assertInvalid("{\"" + ADD + "\": {\"constant\": 1, \"constant\": 2, \"constant\": 3}}", "dValue");
        assertInvalid("{\"" + ADD + "\": {\"constant\": 1, \"wat\": {}}}", "dValue");
        assertInvalid("{\"" + ADD + "\": 3}", "dValue");
    }

    // ------------------------------------------------------------------
    // Draw functions.
    // ------------------------------------------------------------------

    @Test
    void emptyDrawTypesTakeNoFields() throws IOException {
        assertValid("{\"" + NO_TREES + "\": {}}", "drawFunction");
        assertValid("{\"" + OCEAN + "\": {}}", "drawFunction");
        assertValid("{\"" + WATER + "\": {}}", "drawFunction");
        assertInvalid("{\"" + NO_TREES + "\": {\"anything\": 1}}", "drawFunction");
    }

    @Test
    void blockDrawTakesABlockState() throws IOException {
        assertValid("{\"" + BLOCK + "\": {\"id\": \"minecraft:dirt_path\"}}", "drawFunction");
        assertValid(
                "{\"" + BLOCK
                        + "\": {\"id\": \"minecraft:oak_log\", \"properties\": {\"axis\": \"y\", \"level\": 2, \"waterlogged\": false}}}",
                "drawFunction");
        assertInvalid("{\"" + BLOCK + "\": {}}", "drawFunction");
        assertInvalid("{\"" + BLOCK + "\": {\"id\": 3}}", "drawFunction");
        assertInvalid("{\"" + BLOCK + "\": {\"id\": \"minecraft:oak_log\", \"wat\": 1}}", "drawFunction");
        assertInvalid(
                "{\"" + BLOCK + "\": {\"id\": \"minecraft:oak_log\", \"properties\": {\"level\": 1.5}}}",
                "drawFunction");
        assertInvalid(
                "{\"" + BLOCK + "\": {\"id\": \"minecraft:oak_log\", \"properties\": {\"axis\": null}}}",
                "drawFunction");
    }

    @Test
    void weightDrawFunctionsRequireTheirFields() throws IOException {
        assertValid("{\"" + WEIGHT_ADD + "\": {\"delegate\": {\"water\": {}}, \"value\": -4}}", "drawFunction");
        assertInvalid("{\"" + WEIGHT_ADD + "\": {\"delegate\": {\"water\": {}}}}", "drawFunction");
        assertInvalid("{\"" + WEIGHT_ADD + "\": {\"value\": -4}}", "drawFunction");
        assertInvalid("{\"" + WEIGHT_ADD + "\": {\"delegate\": {\"water\": {}}, \"value\": -4.5}}", "drawFunction");
        assertValid(
                "{\"" + WEIGHT_CLAMP + "\": {\"delegate\": {\"water\": {}}, \"min\": 0, \"max\": 3}}", "drawFunction");
        assertValid("{\"" + WEIGHT_CLAMP + "\": {\"delegate\": {\"water\": {}}}}", "drawFunction");
        assertInvalid("{\"" + WEIGHT_CLAMP + "\": {\"min\": 0}}", "drawFunction");
        assertValid(
                "{\"" + WEIGHT_GREATER_THAN
                        + "\": {\"delegate\": {\"block\": {\"id\": \"minecraft:gray_concrete\"}}, \"value\": 3}}",
                "drawFunction");
        assertInvalid(
                "{\"" + WEIGHT_LESS_THAN + "\": {\"delegate\": {\"water\": {}}, \"value\": 1.5}}", "drawFunction");
        assertInvalid(
                "{\"" + WEIGHT_GREATER_THAN + "\": {\"delegate\": {\"water\": {}}, \"value\": 3, \"extra\": 0}}",
                "drawFunction");
    }

    @Test
    void allDrawFunctionHoldsRepeatedTypedMembers() throws IOException {
        assertValid(
                "{\"" + ALL + "\": {\"no_trees\": {}, \"block\": {\"id\": \"minecraft:gray_concrete\"}}}",
                "drawFunction");
        assertValid("{\"" + ALL + "\": {\"no_trees\": {}, \"no_trees\": {}}}", "drawFunction");
        assertValid(
                "{\"" + ALL
                        + "\": {\"weight_greater_than\": {\"delegate\": {\"block\": {\"id\": \"minecraft:gray_concrete\"}}, \"value\": 3}}}",
                "drawFunction");
        assertInvalid("{\"" + ALL + "\": {}}", "drawFunction");
        assertInvalid("{\"" + ALL + "\": {\"wat\": {}}}", "drawFunction");
        assertInvalid("{\"" + ALL + "\": {\"layer\": {\"constant\": 0}}}", "drawFunction");
    }

    @Test
    void unknownDrawFunctionIsRejected() throws IOException {
        assertInvalid("{\"wat\": {}}", "drawFunction");
    }

    // ------------------------------------------------------------------
    // Field positions point at the right unions.
    // ------------------------------------------------------------------

    @Test
    void drawAndLayerFieldsPointAtTheirUnions() throws IOException {
        assertValid("{" + drawAndLayer() + "}", "lineDrawFields");
        assertInvalid("{\"draw\": {\"nothing\": {}}, \"layer\": 0}", "lineDrawFields");
        assertInvalid("{\"draw\": {\"wat\": {}}, \"layer\": {\"constant\": 0}}", "lineDrawFields");
        assertInvalid("{\"draw\": {\"nothing\": {}}, \"layer\": {\"constant\": 0}, \"wat\": 0}", "lineDrawFields");
        assertValid("{" + drawAndLayer() + ", \"radius\": {\"constant\": 1}}", "wideFields");
        assertValid("{" + drawAndLayer() + ", \"maxDist\": 2}", "distanceFields");
        assertValid("{\"line\": {\"narrow\": {" + drawAndLayer() + "}}}", "convertFields");
    }

    @Test
    void defaultTerraConfigRemainsValid() throws IOException {
        String config;
        try (var stream =
                OsmSchemaTest.class.getResourceAsStream("/net/buildtheearth/terraminusminus/dataset/osm/osm.json5")) {
            assertNotNull(stream, "the default osm.json5 must be on the classpath");
            config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertNull(
                validator.validate(DuplicatePreservingJsonReader.read(new StringReader(config))),
                "the default osm.json5 must remain valid");
    }
    // CHECKSTYLE:ON
}
