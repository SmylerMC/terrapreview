package net.smyler.terrapreview.ide.plugin;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.json.psi.JsonFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suppresses IntelliJ's built-in JSON duplicate-key warning inside Terra++ {@code osm.json5}
 * configuration files, where objects with repeated keys are the intended encoding of ordered
 * lists and condition chains.
 */
public final class OsmJsonDuplicateKeySuppressor implements InspectionSuppressor {

    private static final String OSM_CONFIG_FILE_NAME = "osm.json5";
    private static final String DUPLICATE_KEYS_TOOL_ID = "JsonDuplicatePropertyKeys";

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (!DUPLICATE_KEYS_TOOL_ID.equals(toolId)) {
            return false;
        }
        PsiFile file = element.getContainingFile();
        return file instanceof JsonFile
                && OSM_CONFIG_FILE_NAME.equals(file.getName())
                && TerraConfigurationFileRecognizer.isConfigurationFile(file.getVirtualFile());
    }

    @Override
    public @NotNull SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }
}
