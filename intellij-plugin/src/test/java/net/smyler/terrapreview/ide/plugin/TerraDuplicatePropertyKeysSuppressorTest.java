package net.smyler.terrapreview.ide.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.codeInspection.SuppressQuickFix;
import org.junit.jupiter.api.Test;

/**
 * Tests for the duplicate property keys suppressor.
 */
class TerraDuplicatePropertyKeysSuppressorTest {

    private final TerraDuplicatePropertyKeysSuppressor suppressor = new TerraDuplicatePropertyKeysSuppressor();

    @Test
    void matchesOnlyTheDuplicatePropertyKeysInspection() {
        assertTrue(TerraDuplicatePropertyKeysSuppressor.isDuplicatePropertyKeysInspection("JsonDuplicatePropertyKeys"));
        assertFalse(TerraDuplicatePropertyKeysSuppressor.isDuplicatePropertyKeysInspection("Json5StandardCompliance"));
        assertFalse(TerraDuplicatePropertyKeysSuppressor.isDuplicatePropertyKeysInspection("JsonSchemaCompliance"));
        assertFalse(TerraDuplicatePropertyKeysSuppressor.isDuplicatePropertyKeysInspection(""));
    }

    @Test
    void offersNoSuppressQuickFixes() {
        assertSame(SuppressQuickFix.EMPTY_ARRAY, this.suppressor.getSuppressActions(null, "JsonDuplicatePropertyKeys"));
    }
}
