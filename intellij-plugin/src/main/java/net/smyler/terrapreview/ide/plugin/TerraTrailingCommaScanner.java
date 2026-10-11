package net.smyler.terrapreview.ide.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds trailing commas in JSON5 text.
 *
 * <p>Terra-- parses its configuration files with a strict parser that rejects trailing commas,
 * even though they are valid JSON5. This scanner locates the commas that immediately precede the
 * closing {@code ]} or {@code }} of the enclosing array or object, while respecting string and
 * comment syntax.
 */
public final class TerraTrailingCommaScanner {

    /** A trailing comma occurrence, with the half-open range of the comma in the scanned text. */
    public record TrailingComma(int start, int end) {}

    private static final int NONE = -1;

    private TerraTrailingCommaScanner() {}

    /**
     * Scans JSON5 text for trailing commas.
     *
     * @param text the JSON5 text to scan
     * @return the trailing commas, in document order
     */
    public static List<TrailingComma> scan(String text) {
        List<TrailingComma> commas = new ArrayList<>();
        int length = text.length();
        int index = 0;
        int lastSignificant = NONE;
        int lastComma = NONE;
        while (index < length) {
            char c = text.charAt(index);
            switch (c) {
                case '"', '\'' -> {
                    index = skipQuotedString(text, index, c);
                    lastSignificant = index - 1;
                    lastComma = NONE;
                }
                case '/' -> {
                    int after = skipCommentOrSlash(text, index);
                    if (after == index + 1) {
                        lastSignificant = index;
                        lastComma = NONE;
                    }
                    index = after;
                }
                case '[', '{' -> {
                    lastSignificant = NONE;
                    lastComma = NONE;
                    index++;
                }
                case ']', '}' -> {
                    if (lastComma != NONE) {
                        commas.add(new TrailingComma(lastComma, lastComma + 1));
                    }
                    lastSignificant = index;
                    lastComma = NONE;
                    index++;
                }
                case ',' -> {
                    lastSignificant = index;
                    lastComma = index;
                    index++;
                }
                default -> {
                    if (!Character.isWhitespace(c)) {
                        lastSignificant = index;
                        lastComma = NONE;
                    }
                    index++;
                }
            }
        }
        if (lastComma != NONE && lastSignificant == lastComma) {
            commas.add(new TrailingComma(lastComma, lastComma + 1));
        }
        return commas;
    }

    private static int skipQuotedString(String text, int start, char quote) {
        int index = start + 1;
        int length = text.length();
        while (index < length) {
            char c = text.charAt(index);
            if (c == '\\') {
                index += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return index + 1;
            }
            index++;
        }
        return length;
    }

    private static int skipCommentOrSlash(String text, int start) {
        if (start + 1 < text.length()) {
            char next = text.charAt(start + 1);
            if (next == '/') {
                return skipToLineEnd(text, start + 2);
            }
            if (next == '*') {
                return skipBlockComment(text, start + 2);
            }
        }
        return start + 1;
    }

    private static int skipToLineEnd(String text, int index) {
        int length = text.length();
        while (index < length && text.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String text, int index) {
        int length = text.length();
        while (index < length - 1) {
            if (text.charAt(index) == '*' && text.charAt(index + 1) == '/') {
                return index + 2;
            }
            index++;
        }
        return length;
    }
}
