package net.smyler.terrapreview.osmconfig;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON5 reader built on Gson's lenient {@link JsonReader}, preserving duplicate object keys.
 *
 * <p>The Terra++ {@code osm.json5} configuration format uses objects with repeated keys as ordered lists,
 * which the standard JSON data model cannot represent. This reader keeps every member of every object,
 * in document order, so that the semantic rules and the JSON schema can be checked against real
 * configuration files.</p>
 */
public final class DuplicatePreservingJsonReader {

    /**
     * A JSON value: either an object with ordered members, an array, or a primitive or null.
     */
    public interface Value {}

    /** An object whose members are kept in document order, duplicates included. */
    public static final class ObjectValue implements Value {
        private final List<Member> members;

        ObjectValue(List<Member> members) {
            this.members = List.copyOf(members);
        }

        /** @return the object's members, in document order, duplicates included */
        public List<Member> members() {
            return this.members;
        }

        /** @return the member names, in document order */
        public List<String> names() {
            List<String> names = new ArrayList<>();
            for (Member member : this.members) {
                names.add(member.name());
            }
            return names;
        }
    }

    /** A single object member: a name and its value. */
    public record Member(String name, Value value) {}

    /** An array of values. */
    public static final class ArrayValue implements Value {
        private final List<Value> elements;

        ArrayValue(List<Value> elements) {
            this.elements = List.copyOf(elements);
        }

        /** @return the array's elements, in document order */
        public List<Value> elements() {
            return this.elements;
        }
    }

    /** A string, number or boolean literal. */
    public record PrimitiveValue(Object value) implements Value {}

    /** The JSON null value. */
    public record NullValue() implements Value {}

    private DuplicatePreservingJsonReader() {}

    /**
     * Reads a complete JSON5 document, preserving duplicate object keys.
     *
     * @param reader the reader to read from, will be consumed entirely
     * @return the document's root value
     * @throws IOException if the document cannot be parsed
     */
    public static Value read(Reader reader) throws IOException {
        JsonReader in = new JsonReader(reader);
        in.setLenient(true);
        Value value = readValue(in);
        return value;
    }

    private static Value readValue(JsonReader in) throws IOException {
        return switch (in.peek()) {
            case BEGIN_OBJECT -> readObject(in);
            case BEGIN_ARRAY -> readArray(in);
            case STRING -> new PrimitiveValue(in.nextString());
            case NUMBER -> new PrimitiveValue(parseNumber(in.nextString()));
            case BOOLEAN -> new PrimitiveValue(in.nextBoolean());
            case NULL -> {
                in.nextNull();
                yield new NullValue();
            }
            default -> throw new IOException("unexpected token: " + in.peek());
        };
    }

    private static Value readObject(JsonReader in) throws IOException {
        List<Member> members = new ArrayList<>();
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            members.add(new Member(name, readValue(in)));
        }
        in.endObject();
        return new ObjectValue(members);
    }

    private static Value readArray(JsonReader in) throws IOException {
        List<Value> elements = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            elements.add(readValue(in));
        }
        in.endArray();
        return new ArrayValue(elements);
    }

    /**
     * Converts a value to its plain-Java representation, for use with a generic JSON schema validator.
     *
     * @param value the value to convert
     * @return the value as plain Java objects (maps, lists, strings, numbers, booleans, null)
     */
    public static Object toPlain(Value value) {
        if (value instanceof ObjectValue object) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Member member : object.members()) {
                out.put(member.name(), toPlain(member.value()));
            }
            return out;
        } else if (value instanceof ArrayValue array) {
            List<Object> out = new ArrayList<>();
            for (Value element : array.elements()) {
                out.add(toPlain(element));
            }
            return out;
        } else if (value instanceof PrimitiveValue primitive) {
            return primitive.value();
        } else {
            return null;
        }
    }

    private static Number parseNumber(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
        }
        return Double.parseDouble(token);
    }
}
