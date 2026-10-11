package net.smyler.terrapreview.ide.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the trailing comma scanner.
 */
class TerraTrailingCommaScannerTest {

    @Test
    void findsTrailingCommaInObject() {
        assertEquals(List.of(new TerraTrailingCommaScanner.TrailingComma(15, 16)),
                TerraTrailingCommaScanner.scan("{\"key\": \"value\",}"));
    }

    @Test
    void findsTrailingCommaInArray() {
        assertEquals(List.of(new TerraTrailingCommaScanner.TrailingComma(8, 9)),
                TerraTrailingCommaScanner.scan("[1, 2, 3,]"));
    }

    @Test
    void findsMultipleTrailingCommas() {
        List<TerraTrailingCommaScanner.TrailingComma> commas =
                TerraTrailingCommaScanner.scan("{\"a\": [1,], \"b\": {\"c\": 1,},}");
        assertEquals(3, commas.size());
    }

    @Test
    void ignoresCommasBeforeValues() {
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{\"key\": \"value\", \"other\": 1}"));
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("[1, 2, 3]"));
    }

    @Test
    void ignoresCommasInsideStrings() {
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{\"key\": \"a, }\", \"other\": 'b,]'}"));
    }

    @Test
    void ignoresCommasInsideComments() {
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{\n  // a comment with a comma,\n  \"key\": 1\n}"));
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{\n  /* a comment with a comma, and a bracket } */\n  \"key\": 1\n}"));
    }

    @Test
    void findsTrailingCommaSeparatedFromBracketByComment() {
        // A comma followed only by a comment before the closing bracket is still trailing.
        List<TerraTrailingCommaScanner.TrailingComma> commas =
                TerraTrailingCommaScanner.scan("{\"key\": 1, // last\n}");
        assertEquals(1, commas.size());
    }

    @Test
    void findsTrailingCommaSeparatedFromBracketByWhitespace() {
        List<TerraTrailingCommaScanner.TrailingComma> commas =
                TerraTrailingCommaScanner.scan("[\n    1,\n    2,\n]");
        assertEquals(1, commas.size());
    }

    @Test
    void handlesNestedContainers() {
        List<TerraTrailingCommaScanner.TrailingComma> commas =
                TerraTrailingCommaScanner.scan("[{\"a\": 1,}, {\"b\": 2}]");
        assertEquals(1, commas.size());
        assertEquals(8, commas.get(0).start());
    }

    @Test
    void handlesEscapedQuotesInStrings() {
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{\"key\": \"a\\\", b\"}"));
    }

    @Test
    void handlesEmptyContainers() {
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{}"));
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("[]"));
        assertEquals(List.of(), TerraTrailingCommaScanner.scan(""));
    }

    @Test
    void handlesUnbalancedText() {
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("{\"key\": 1"));
        assertEquals(List.of(), TerraTrailingCommaScanner.scan("}"));
    }
}
