package net.smyler.terrapreview.ide.plugin;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import net.smyler.terrapreview.osmconfig.OsmConfigRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic inspection for Terra++ {@code osm.json5} configuration files.
 *
 * <p>The format encodes typed unions and ordered lists as JSON objects whose keys repeat in
 * document order, which standard JSON Schema cannot validate. This inspection walks the JSON
 * PSI and reports the rule violations computed by {@link OsmConfigRules}: union objects must
 * have exactly one discriminant member with a known type name, list objects must have at least
 * one member with known type names, and binary {@code DValue} operators must have exactly two
 * operands.</p>
 */
public final class OsmJsonInspection extends LocalInspectionTool {

    private static final String OSM_CONFIG_FILE_NAME = "osm.json5";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        var file = holder.getFile();
        if (!(file instanceof JsonFile)
                || !OSM_CONFIG_FILE_NAME.equals(file.getName())
                || !TerraConfigurationFileRecognizer.isConfigurationFile(file.getVirtualFile())) {
            return new PsiElementVisitor() {
            };
        }
        return new Visitor(holder);
    }

    private static final class Visitor extends JsonRecursiveElementVisitor {
        private final ProblemsHolder holder;

        private Visitor(@NotNull ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitObject(@NotNull JsonObject object) {
            super.visitObject(object);

            JsonProperty property = enclosingProperty(object);
            if (property == null) {
                return;
            }
            String role = property.getName();
            List<String> memberNames = new ArrayList<>();
            for (JsonProperty member : object.getPropertyList()) {
                memberNames.add(member.getName());
            }
            String problem = OsmConfigRules.check(role, ancestorPropertyNames(property), memberNames);
            if (problem != null) {
                this.holder.registerProblem(object, problem);
            }
        }

        @Nullable
        private static JsonProperty enclosingProperty(@NotNull PsiElement element) {
            PsiElement parent = element.getParent();
            return parent instanceof JsonProperty property ? property : null;
        }

        /**
         * The names of the properties enclosing the given property, from nearest to farthest.
         */
        private static List<String> ancestorPropertyNames(@NotNull JsonProperty property) {
            List<String> names = new ArrayList<>();
            PsiElement current = property.getParent();
            while (current instanceof JsonObject object) {
                JsonProperty enclosing = enclosingProperty(object);
                if (enclosing == null) {
                    return names;
                }
                names.add(enclosing.getName());
                current = enclosing.getParent();
            }
            return names;
        }
    }
}
