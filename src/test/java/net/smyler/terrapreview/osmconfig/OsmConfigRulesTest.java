package net.smyler.terrapreview.osmconfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link OsmConfigRules}, the semantic rules of the Terra++ osm.json5 format.
 */
class OsmConfigRulesTest {

    private static final List<String> NO_PARENTS = List.of();

    @Test
    void rootHasNoRule() {
        assertNull(OsmConfigRules.check("unknown", NO_PARENTS, List.of("anything")));
    }

    @Test
    void mapperUnionAcceptsKnownLineType() {
        assertNull(OsmConfigRules.check("line", NO_PARENTS, List.of("first")));
        assertNull(OsmConfigRules.check("line", NO_PARENTS, List.of("wide")));
        assertNull(OsmConfigRules.check("polygon", NO_PARENTS, List.of("distance")));
    }

    @Test
    void mapperUnionRejectsUnknownType() {
        assertEquals("unknown mapper type: wat",
                OsmConfigRules.check("line", NO_PARENTS, List.of("wat")));
    }

    @Test
    void emitAcceptsBothLineAndPolygonTypes() {
        assertNull(OsmConfigRules.check("emit", NO_PARENTS, List.of("narrow")));
        assertNull(OsmConfigRules.check("emit", NO_PARENTS, List.of("fill")));
    }

    @Test
    void unionMustHaveExactlyOneMember() {
        assertEquals("a mapper must contain exactly one member naming its type, found 2",
                OsmConfigRules.check("line", NO_PARENTS, List.of("first", "wide")));
        assertEquals("a match condition must contain exactly one member naming its type, found 0",
                OsmConfigRules.check("match", NO_PARENTS, List.of()));
    }

    @Test
    void matchUnionAcceptsKnownTypes() {
        for (String type : OsmConfigRules.MATCH_CONDITIONS) {
            assertNull(OsmConfigRules.check("match", NO_PARENTS, List.of(type)), type);
        }
    }

    @Test
    void matchUnionRejectsMapperType() {
        assertEquals("unknown match condition type: wide",
                OsmConfigRules.check("match", NO_PARENTS, List.of("wide")));
    }

    @Test
    void drawUnionAcceptsKnownTypes() {
        for (String type : OsmConfigRules.DRAW_FUNCTIONS) {
            assertNull(OsmConfigRules.check("draw", NO_PARENTS, List.of(type)), type);
            assertNull(OsmConfigRules.check("delegate", NO_PARENTS, List.of(type)), type);
        }
    }

    @Test
    void dvalueUnionAcceptsKnownTypes() {
        for (String type : OsmConfigRules.DVALUE_TYPES) {
            assertNull(OsmConfigRules.check("layer", NO_PARENTS, List.of(type)), type);
            assertNull(OsmConfigRules.check("radius", NO_PARENTS, List.of(type)), type);
        }
    }

    @Test
    void andOrAreMatchLists() {
        assertNull(OsmConfigRules.check("and", NO_PARENTS, List.of("tag", "not", "tag")));
        assertEquals("unknown match condition type: wide",
                OsmConfigRules.check("or", NO_PARENTS, List.of("wide")));
        assertEquals("at least one match condition member is required",
                OsmConfigRules.check("and", NO_PARENTS, List.of()));
    }

    @Test
    void firstAllAnyAreMapperLists() {
        assertNull(OsmConfigRules.check("first", NO_PARENTS, List.of("condition", "condition", "wide", "narrow")));
        assertEquals("unknown mapper type: river",
                OsmConfigRules.check("first", NO_PARENTS, List.of("river")));
        assertEquals("at least one mapper member is required",
                OsmConfigRules.check("all", NO_PARENTS, List.of()));
    }

    @Test
    void firstInsideDrawIsADrawFunctionList() {
        assertNull(OsmConfigRules.check("all", List.of("draw"), List.of("no_trees", "block")));
        assertEquals("unknown draw function type: condition",
                OsmConfigRules.check("all", List.of("draw"), List.of("condition")));
    }

    @Test
    void firstInsideDelegateIsADrawFunctionList() {
        assertNull(OsmConfigRules.check("first", List.of("delegate"), List.of("water")));
        assertEquals("unknown draw function type: narrow",
                OsmConfigRules.check("first", List.of("delegate"), List.of("narrow")));
    }

    @Test
    void firstInsideNestedDrawContextIsADrawFunctionList() {
        // all > weight_greater_than > delegate > all
        assertNull(OsmConfigRules.check("all", List.of("delegate", "weight_greater_than", "all", "draw"), List.of("water")));
    }

    @Test
    void binaryOperatorsTakeExactlyTwoOperands() {
        assertNull(OsmConfigRules.check("+", NO_PARENTS, List.of("constant", "tag")));
        assertNull(OsmConfigRules.check("floor_div", NO_PARENTS, List.of("*", "+")));
        assertEquals("binary DValue operators take exactly two operands, found 1",
                OsmConfigRules.check("+", NO_PARENTS, List.of("constant")));
        assertEquals("binary DValue operators take exactly two operands, found 3",
                OsmConfigRules.check("max", NO_PARENTS, List.of("constant", "constant", "constant")));
    }

    @Test
    void binaryOperandsMustBeDValues() {
        assertEquals("unknown DValue type: wide",
                OsmConfigRules.check("+", NO_PARENTS, List.of("constant", "wide")));
    }

    @Test
    void binaryOperandsAreDValueUnions() {
        // the operands of a binary op are DValue unions: a single "tag" member is valid
        assertNull(OsmConfigRules.check("tag", NO_PARENTS, List.of("key", "fallback")));
    }

    @Test
    void lineInsideConvertIsALineMapperOnly() {
        assertNull(OsmConfigRules.check("line", List.of("convert"), List.of("narrow")));
        assertEquals("unknown line mapper type: fill",
                OsmConfigRules.check("line", List.of("convert"), List.of("fill")));
    }

    @Test
    void unknownPropertyHasNoRule() {
        assertNull(OsmConfigRules.check("maxDist", NO_PARENTS, List.of("whatever")));
    }

    @Test
    void drawContextDetectionOnlyLooksAtNearestParent() {
        // "all" whose nearest enclosing property is "layer" is not a draw context,
        // so its members are mappers, not draw functions
        assertEquals("unknown mapper type: tag",
                OsmConfigRules.check("all", List.of("layer"), List.of("tag")));
    }
}
