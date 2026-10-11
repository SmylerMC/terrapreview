package net.smyler.terrapreview.ide.plugin;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suppresses the JSON duplicate property keys warning for Terra-- configuration files.
 *
 * <p>Terra-- relies on duplicate keys in some of its configuration files, for example
 * {@code osm.json5}, and its configuration loader gives them a meaning. The configuration is
 * therefore valid even though the built-in {@code JsonDuplicatePropertyKeys} inspection reports it.
 */
public final class TerraDuplicatePropertyKeysSuppressor implements InspectionSuppressor {

    private static final String DUPLICATE_PROPERTY_KEYS_INSPECTION = "JsonDuplicatePropertyKeys";

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (!isDuplicatePropertyKeysInspection(toolId)) {
            return false;
        }
        var file = element.getContainingFile();
        return file != null && TerraConfigurationFileRecognizer.isConfigurationFile(file.getVirtualFile());
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }

    static boolean isDuplicatePropertyKeysInspection(@NotNull String toolId) {
        return DUPLICATE_PROPERTY_KEYS_INSPECTION.equals(toolId);
    }
}
