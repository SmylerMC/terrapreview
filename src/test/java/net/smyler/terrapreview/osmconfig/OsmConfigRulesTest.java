package net.smyler.terrapreview.osmconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OsmConfigRules}, the semantic rules of the Terra++ osm.json5 format.
 */
class OsmConfigRulesTest {

    private static final List<String> NO_PARENTS = List.of();

    private static final String LINE = "line";
    private static final String POLYGON = "polygon";
    private static final String EMIT = "emit";
    private static final String MATCH = "match";
    private static final String DRAW = "draw";
    private static final String DELEGATE = "delegate";
    private static final String LAYER = "layer";
    private static final String RADIUS = "radius";
    private static final String AND = "and";
    private static final String OR = "or";
    private static final String ALL = "all";
    private static final String FIRST = "first";
    private static final String CONDITION = "condition";
    private static final String NARROW = "narrow";
    private static final String WIDE = "wide";
    private static final String FILL = "fill";
    private static final String TAG = "tag";
    private static final String NOT = "not";
    private static final String CONSTANT = "constant";
    private static final String ADD = "+";
    private static final String WATER = "water";
    private static final String NO_TREES = "no_trees";
    private static final String BLOCK = "block";
    private static final String DISTANCE = "distance";
    private static final String CONVERT = "convert";
    private static final String FLOOR_DIV = "floor_div";
    private static final String MULTIPLY = "*";
    private static final String MAX = "max";

    @Test
    void rootHasNoRule() {
        assertNull(OsmConfigRules.check("unknown", NO_PARENTS, List.of("anything")));
    }

    @Test
    void mapperUnionAcceptsKnownLineType() {
        assertNull(OsmConfigRules.check(LINE, NO_PARENTS, List.of(FIRST)));
        assertNull(OsmConfigRules.check(LINE, NO_PARENTS, List.of(WIDE)));
        assertNull(OsmConfigRules.check(POLYGON, NO_PARENTS, List.of(DISTANCE)));
    }

    @Test
    void mapperUnionRejectsUnknownType() {
        assertEquals("unknown mapper type: wat", OsmConfigRules.check(LINE, NO_PARENTS, List.of("wat")));
    }

    @Test
    void emitAcceptsBothLineAndPolygonTypes() {
        assertNull(OsmConfigRules.check(EMIT, NO_PARENTS, List.of(NARROW)));
        assertNull(OsmConfigRules.check(EMIT, NO_PARENTS, List.of(FILL)));
    }

    @Test
    void unionMustHaveExactlyOneMember() {
        assertEquals(
                "a mapper must contain exactly one member naming its type, found 2",
                OsmConfigRules.check(LINE, NO_PARENTS, List.of(FIRST, WIDE)));
        assertEquals(
                "a match condition must contain exactly one member naming its type, found 0",
                OsmConfigRules.check(MATCH, NO_PARENTS, List.of()));
    }

    @Test
    void matchUnionAcceptsKnownTypes() {
        for (String type : OsmConfigRules.MATCH_CONDITIONS) {
            assertNull(OsmConfigRules.check(MATCH, NO_PARENTS, List.of(type)), type);
        }
    }

    @Test
    void matchUnionRejectsMapperType() {
        assertEquals("unknown match condition type: wide", OsmConfigRules.check(MATCH, NO_PARENTS, List.of(WIDE)));
    }

    @Test
    void drawUnionAcceptsKnownTypes() {
        for (String type : OsmConfigRules.DRAW_FUNCTIONS) {
            assertNull(OsmConfigRules.check(DRAW, NO_PARENTS, List.of(type)), type);
            assertNull(OsmConfigRules.check(DELEGATE, NO_PARENTS, List.of(type)), type);
        }
    }

    @Test
    void dvalueUnionAcceptsKnownTypes() {
        for (String type : OsmConfigRules.DVALUE_TYPES) {
            assertNull(OsmConfigRules.check(LAYER, NO_PARENTS, List.of(type)), type);
            assertNull(OsmConfigRules.check(RADIUS, NO_PARENTS, List.of(type)), type);
        }
    }

    @Test
    void andOrAreMatchLists() {
        assertNull(OsmConfigRules.check(AND, NO_PARENTS, List.of(TAG, NOT, TAG)));
        assertEquals("unknown match condition type: wide", OsmConfigRules.check(OR, NO_PARENTS, List.of(WIDE)));
        assertEquals(
                "at least one match condition member is required", OsmConfigRules.check(AND, NO_PARENTS, List.of()));
    }

    @Test
    void firstAllAnyAreMapperLists() {
        assertNull(OsmConfigRules.check(FIRST, NO_PARENTS, List.of(CONDITION, CONDITION, WIDE, NARROW)));
        assertEquals("unknown mapper type: river", OsmConfigRules.check(FIRST, NO_PARENTS, List.of("river")));
        assertEquals("at least one mapper member is required", OsmConfigRules.check(ALL, NO_PARENTS, List.of()));
    }

    @Test
    void firstInsideDrawIsADrawFunctionList() {
        assertNull(OsmConfigRules.check(ALL, List.of(DRAW), List.of(NO_TREES, BLOCK)));
        assertEquals(
                "unknown draw function type: condition", OsmConfigRules.check(ALL, List.of(DRAW), List.of(CONDITION)));
    }

    @Test
    void firstInsideDelegateIsADrawFunctionList() {
        assertNull(OsmConfigRules.check(FIRST, List.of(DELEGATE), List.of(WATER)));
        assertEquals(
                "unknown draw function type: narrow", OsmConfigRules.check(FIRST, List.of(DELEGATE), List.of(NARROW)));
    }

    @Test
    void firstInsideNestedDrawContextIsADrawFunctionList() {
        // all > weight_greater_than > delegate > all
        assertNull(OsmConfigRules.check(ALL, List.of(DELEGATE, "weight_greater_than", ALL, DRAW), List.of(WATER)));
    }

    @Test
    void binaryOperatorsTakeExactlyTwoOperands() {
        assertNull(OsmConfigRules.check(ADD, NO_PARENTS, List.of(CONSTANT, TAG)));
        assertNull(OsmConfigRules.check(FLOOR_DIV, NO_PARENTS, List.of(MULTIPLY, ADD)));
        assertEquals(
                "binary DValue operators take exactly two operands, found 1",
                OsmConfigRules.check(ADD, NO_PARENTS, List.of(CONSTANT)));
        assertEquals(
                "binary DValue operators take exactly two operands, found 3",
                OsmConfigRules.check(MAX, NO_PARENTS, List.of(CONSTANT, CONSTANT, CONSTANT)));
    }

    @Test
    void binaryOperandsMustBeDValues() {
        assertEquals("unknown DValue type: wide", OsmConfigRules.check(ADD, NO_PARENTS, List.of(CONSTANT, WIDE)));
    }

    @Test
    void binaryOperandsAreDValueUnions() {
        // the operands of a binary op are DValue unions: a single "tag" member is valid
        assertNull(OsmConfigRules.check(TAG, NO_PARENTS, List.of("key", "fallback")));
    }

    @Test
    void lineInsideConvertIsALineMapperOnly() {
        assertNull(OsmConfigRules.check(LINE, List.of(CONVERT), List.of(NARROW)));
        assertEquals("unknown line mapper type: fill", OsmConfigRules.check(LINE, List.of(CONVERT), List.of(FILL)));
    }

    @Test
    void unknownPropertyHasNoRule() {
        assertNull(OsmConfigRules.check("maxDist", NO_PARENTS, List.of("whatever")));
    }

    @Test
    void drawContextDetectionOnlyLooksAtNearestParent() {
        // "all" whose nearest enclosing property is "layer" is not a draw context,
        // so its members are mappers, not draw functions
        assertEquals("unknown mapper type: tag", OsmConfigRules.check(ALL, List.of(LAYER), List.of(TAG)));
    }
}
