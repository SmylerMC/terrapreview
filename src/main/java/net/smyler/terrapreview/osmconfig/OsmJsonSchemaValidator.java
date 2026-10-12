package net.smyler.terrapreview.osmconfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates documents against the Terra++ {@code osm.json5} JSON schema.
 *
 * <p>The schema (bundled as a resource) describes the format's typed unions and ordered lists, which are
 * represented as objects with possibly repeated keys. This validator checks the schema's structural
 * subset: {@code $ref}, {@code oneOf}, {@code type}, {@code properties}, {@code additionalProperties},
 * {@code required}, and property count bounds. The duplicate-preserving reader keeps every member of
 * every object, so ordered lists validate member by member, the way Terra++ itself parses them.</p>
 */
public final class OsmJsonSchemaValidator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String PROPERTIES = "properties";
    private static final int MAX_REFERENCE_DEPTH = 16;

    private final Map<String, Object> schema;

    private OsmJsonSchemaValidator(Map<String, Object> schema) {
        this.schema = schema;
    }

    /**
     * Loads the bundled {@code osm.json5} schema.
     *
     * @return the validator for the bundled schema
     */
    public static OsmJsonSchemaValidator loadBundled() {
        return new OsmJsonSchemaValidator(
                readSchema(OsmJsonSchemaValidator.class.getResourceAsStream("osm-schema.json")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readSchema(InputStream stream) {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, Object> schema = new java.util.HashMap<>();
            for (Map.Entry<String, JsonElement> entry :
                    GSON.fromJson(reader, JsonObject.class).entrySet()) {
                schema.put(entry.getKey(), GSON.fromJson(entry.getValue(), Object.class));
            }
            return schema;
        } catch (IOException e) {
            throw new IllegalStateException("could not read the bundled osm.json5 schema", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> defs() {
        return (Map<String, Object>) this.schema.get("$defs");
    }

    /**
     * Validates a duplicate-preserving document against the schema.
     *
     * @param value the document's root value, as produced by {@link DuplicatePreservingJsonReader}
     * @return null when the document is valid, otherwise a description of the first violation found
     */
    public String validate(DuplicatePreservingJsonReader.Value value) {
        return this.check(value, this.schema, "");
    }

    @SuppressWarnings("unchecked")
    private String check(DuplicatePreservingJsonReader.Value value, Object node, String path) {
        node = this.resolve(node);
        if (!(node instanceof Map)) {
            return null;
        }
        Map<String, Object> schemaObject = (Map<String, Object>) node;

        Object oneOf = schemaObject.get("oneOf");
        if (oneOf instanceof List<?> alternatives) {
            String firstFailure = null;
            for (Object alternative : alternatives) {
                String failure = this.check(value, alternative, path);
                if (failure == null) {
                    return null;
                }
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
            return (firstFailure == null ? "does not match any allowed form" : firstFailure) + " (" + path + ")";
        }

        Object typeNode = schemaObject.get("type");
        if (typeNode instanceof String type) {
            String typeFailure = this.checkType(value, type, path);
            if (typeFailure != null) {
                return typeFailure;
            }
            if ("object".equals(type) && value instanceof DuplicatePreservingJsonReader.ObjectValue object) {
                return this.checkObject(object, schemaObject, path);
            }
        }
        return null;
    }

    private String checkType(DuplicatePreservingJsonReader.Value value, String type, String path) {
        return switch (type) {
            case "object" ->
                value instanceof DuplicatePreservingJsonReader.ObjectValue ? null : path + ": expected an object";
            case "array" ->
                value instanceof DuplicatePreservingJsonReader.ArrayValue ? null : path + ": expected an array";
            case "string" ->
                value instanceof DuplicatePreservingJsonReader.PrimitiveValue primitive
                                && primitive.value() instanceof String
                        ? null
                        : path + ": expected a string";
            case "number" ->
                value instanceof DuplicatePreservingJsonReader.PrimitiveValue primitive
                                && primitive.value() instanceof Number
                        ? null
                        : path + ": expected a number";
            case "integer" ->
                value instanceof DuplicatePreservingJsonReader.PrimitiveValue primitive
                                && primitive.value() instanceof Long
                        ? null
                        : path + ": expected an integer";
            case "boolean" ->
                value instanceof DuplicatePreservingJsonReader.PrimitiveValue primitive
                                && primitive.value() instanceof Boolean
                        ? null
                        : path + ": expected a boolean";
            case "null" -> value instanceof DuplicatePreservingJsonReader.NullValue ? null : path + ": expected null";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private String checkObject(
            DuplicatePreservingJsonReader.ObjectValue object, Map<String, Object> schemaObject, String path) {
        List<DuplicatePreservingJsonReader.Member> members = object.members();
        int count = members.size();
        Integer min = asInteger(schemaObject.get("minProperties"));
        Integer max = asInteger(schemaObject.get("maxProperties"));
        if (min != null && count < min) {
            return path + ": expected at least " + min + " member(s), found " + count;
        }
        if (max != null && count > max) {
            return path + ": expected at most " + max + " member(s), found " + count;
        }

        Object requiredNode = schemaObject.get("required");
        if (requiredNode instanceof List<?> required) {
            java.util.Set<String> present = new java.util.HashSet<>();
            for (DuplicatePreservingJsonReader.Member member : members) {
                present.add(member.name());
            }
            for (Object name : required) {
                if (!present.contains(name)) {
                    return path + ": missing required member \"" + name + "\"";
                }
            }
        }

        Map<String, Object> properties = asMap(schemaObject.get(PROPERTIES));
        Object additional = schemaObject.get("additionalProperties");
        for (DuplicatePreservingJsonReader.Member member : members) {
            String childPath = path + "/" + member.name();
            Object memberSchema = properties == null ? null : properties.get(member.name());
            if (memberSchema == null) {
                if (Boolean.FALSE.equals(additional)) {
                    return childPath + ": unknown key \"" + member.name() + "\"";
                }
                if (additional instanceof Map) {
                    String failure = this.check(member.value(), additional, childPath);
                    if (failure != null) {
                        return failure;
                    }
                }
            } else {
                String failure = this.check(member.value(), memberSchema, childPath);
                if (failure != null) {
                    return failure;
                }
            }
        }
        return null;
    }

    private Object resolve(Object node) {
        int depth = 0;
        while (node instanceof Map<?, ?> map && map.get("$ref") instanceof String ref && depth < MAX_REFERENCE_DEPTH) {
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            node = this.defs().get(name);
            depth++;
        }
        return node;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? Integer.valueOf(number.intValue()) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /**
     * Returns the member names accepted by the schema node at the given property path, for tests.
     *
     * @param path the property names from the root, naming the schema node to inspect
     * @return the accepted property names, or null when the node has no named properties
     */
    public Set<String> acceptedPropertyNames(List<String> path) {
        Object node = this.schema;
        for (String name : path) {
            node = this.resolve(node);
            if (!(node instanceof Map<?, ?>)) {
                return null;
            }
            Map<String, Object> schemaObject = (Map<String, Object>) node;
            Object properties = schemaObject.get(PROPERTIES);
            if (properties instanceof Map<?, ?> propertyMap && propertyMap.get(name) != null) {
                node = propertyMap.get(name);
                continue;
            }
            Object additional = schemaObject.get("additionalProperties");
            if (additional instanceof Map || Boolean.TRUE.equals(additional)) {
                node = additional;
            } else {
                return null;
            }
        }
        node = this.resolve(node);
        if (node instanceof Map<?, ?> map
                && ((Map<String, Object>) map).get(PROPERTIES) instanceof Map<?, ?> properties) {
            return ((Map<String, Object>) properties).keySet();
        }
        return null;
    }
}
