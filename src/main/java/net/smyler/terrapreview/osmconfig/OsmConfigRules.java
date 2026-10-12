package net.smyler.terrapreview.osmconfig;

import java.util.List;
import java.util.Set;

/**
 * Pure semantic rules of the Terra++ {@code osm.json5} configuration format, used by the
 * IntelliJ plugin's inspection and covered by unit tests.
 *
 * <p>The format encodes typed unions and ordered lists as JSON objects whose keys repeat in
 * document order. The inspection walks the JSON PSI and asks this class which rule applies to
 * each object, based on its position in the tree; JSON Schema cannot express these rules.</p>
 */
public final class OsmConfigRules {

    private static final String ALL = "all";
    private static final String ANY = "any";
    private static final String FIRST = "first";
    private static final String UNKNOWN = "unknown ";
    private static final String TYPE_SUFFIX = " type: ";

    public static final Set<String> LINE_MAPPERS = Set.of(
            ALL, ANY, "condition", FIRST, "nothing", "narrow", "sharp", "wide");
    public static final Set<String> POLYGON_MAPPERS = Set.of(
            ALL, ANY, "condition", "convert", FIRST, "nothing", "distance", "fill");
    public static final Set<String> MATCH_CONDITIONS = Set.of(
            "and", "or", "not", "tag", "id", "intersects");
    public static final Set<String> DRAW_FUNCTIONS = Set.of(
            "all", "block", "no_trees", "ocean", "water",
            "weight_add", "weight_clamp", "weight_greater_than", "weight_less_than");
    public static final Set<String> DVALUE_TYPES = Set.of(
            "constant", "tag", "+", "-", "*", "/", "floor_div", "min", "max");
    public static final Set<String> BINARY_DVALUE_OPS = Set.of(
            "+", "-", "*", "/", "floor_div", "min", "max");
    private static final Set<String> DRAW_CONTEXTS = Set.of("draw", "delegate");

    private OsmConfigRules() {
    }

    /**
     * Returns the rule that applies to the object that is the value of the given property, or {@code null}
     * if the object is not type-constrained at this position.
     *
     * @param propertyName the name of the property whose value is the object
     * @param parentPropertyNames the names of the properties enclosing this property, from nearest to farthest
     * @return the applicable rule, or null
     */
    public static Rule ruleFor(String propertyName, List<String> parentPropertyNames) {
        if ("line".equals(propertyName) && parentPropertyNames != null
                && !parentPropertyNames.isEmpty() && "convert".equals(parentPropertyNames.get(0))) {
            return Rule.union(LINE_MAPPERS, null, "line mapper");
        }
        return switch (propertyName) {
            case "line", "polygon", "emit" -> Rule.union(LINE_MAPPERS, POLYGON_MAPPERS, "mapper");
            case "match", "not" -> Rule.union(MATCH_CONDITIONS, null, "match condition");
            case "draw", "delegate" -> Rule.union(DRAW_FUNCTIONS, null, "draw function");
            case "layer", "radius" -> Rule.union(DVALUE_TYPES, null, "DValue");
            case "and", "or" -> Rule.list(MATCH_CONDITIONS, "match condition");
            case ALL, ANY, FIRST -> isDrawContext(parentPropertyNames)
                    ? Rule.list(DRAW_FUNCTIONS, "draw function")
                    : Rule.list(union(LINE_MAPPERS, POLYGON_MAPPERS), "mapper");
            default -> BINARY_DVALUE_OPS.contains(propertyName)
                    ? Rule.binaryOperands(DVALUE_TYPES, "DValue")
                    : null;
        };
    }

    private static boolean isDrawContext(List<String> parentPropertyNames) {
        return parentPropertyNames != null && !parentPropertyNames.isEmpty()
                && DRAW_CONTEXTS.contains(parentPropertyNames.get(0));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        return Set.of(java.util.stream.Stream.concat(first.stream(), second.stream())
                .toArray(String[]::new));
    }

    /**
     * A semantic rule that applies to a configuration object.
     */
    public interface Rule {
        /**
         * Checks the given member names of an object under this rule.
         *
         * @param memberNames the names of the object's members, in document order
         * @return a problem message, or null if the members are valid
         */
        String check(List<String> memberNames);

        /**
         * Creates a rule for a typed union object: exactly one member, whose key names the type.
         *
         * @param types the accepted type names
         * @param moreTypes additional accepted type names, or null
         * @param what a human-readable name of the union, used in problem messages
         * @return the rule
         */
        static Rule union(Set<String> types, Set<String> moreTypes, String what) {
            return memberNames -> {
                if (memberNames.size() != 1) {
                    return "a " + what + " must contain exactly one member naming its type, found " + memberNames.size();
                }
                String type = memberNames.get(0);
                if (!types.contains(type) && (moreTypes == null || !moreTypes.contains(type))) {
                    return UNKNOWN + what + TYPE_SUFFIX + type;
                }
                return null;
            };
        }

        /**
         * Creates a rule for an ordered list object: at least one member, each key naming a valid child type.
         *
         * @param childTypes the accepted child type names
         * @param what a human-readable name of the children, used in problem messages
         * @return the rule
         */
        static Rule list(Set<String> childTypes, String what) {
            return memberNames -> {
                if (memberNames.isEmpty()) {
                    return "at least one " + what + " member is required";
                }
                for (String name : memberNames) {
                    if (!childTypes.contains(name)) {
                        return UNKNOWN + what + TYPE_SUFFIX + name;
                    }
                }
                return null;
            };
        }

        /**
         * Creates a rule for a binary operator object: exactly two members, each key naming a valid operand type.
         *
         * @param operandTypes the accepted operand type names
         * @param what a human-readable name of the operands, used in problem messages
         * @return the rule
         */
        static Rule binaryOperands(Set<String> operandTypes, String what) {
            return memberNames -> {
                if (memberNames.size() != 2) {
                    return "binary " + what + " operators take exactly two operands, found " + memberNames.size();
                }
                for (String name : memberNames) {
                    if (!operandTypes.contains(name)) {
                        return UNKNOWN + what + TYPE_SUFFIX + name;
                    }
                }
                return null;
            };
        }
    }

    /**
     * Checks the member names of a configuration object against the rule for its position.
     *
     * @param propertyName the name of the property whose value is the object
     * @param parentPropertyNames the names of the properties enclosing this property, from nearest to farthest
     * @param memberNames the names of the object's members, in document order
     * @return a problem message, or null if the members are valid
     */
    public static String check(String propertyName, List<String> parentPropertyNames, List<String> memberNames) {
        Rule rule = ruleFor(propertyName, parentPropertyNames);
        return rule == null ? null : rule.check(memberNames);
    }
}
