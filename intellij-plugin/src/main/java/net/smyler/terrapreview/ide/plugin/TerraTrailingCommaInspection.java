package net.smyler.terrapreview.ide.plugin;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports trailing commas in Terra-- JSON5 configuration files.
 *
 * <p>Trailing commas are valid JSON5, but the Terra-- configuration parser rejects them, so they
 * would otherwise only surface as a configuration load failure at runtime.
 */
public final class TerraTrailingCommaInspection extends LocalInspectionTool {

    private static final String PROBLEM_DESCRIPTION =
            "Trailing comma: the Terra-- configuration parser does not support trailing commas";

    @Override
    public boolean isAvailableForFile(@NotNull PsiFile file) {
        return TerraConfigurationFileRecognizer.isConfigurationFile(file.getVirtualFile());
    }

    @Override
    public ProblemDescriptor @Nullable [] checkFile(
            @NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        CharSequence contents = file.getViewProvider().getContents();
        if (contents == null) {
            return null;
        }
        String text = contents.toString();
        if (!text.contains(",")) {
            return null;
        }
        ProblemDescriptor[] descriptors = TerraTrailingCommaScanner.scan(text).stream()
                .map(comma -> manager.createProblemDescriptor(
                        file,
                        TextRange.create(comma.start(), comma.end()),
                        PROBLEM_DESCRIPTION,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        isOnTheFly,
                        new RemoveTrailingCommaFix()))
                .toArray(ProblemDescriptor[]::new);
        return descriptors.length == 0 ? null : descriptors;
    }

    /** Removes a trailing comma from the document. */
    private static final class RemoveTrailingCommaFix implements LocalQuickFix {

        @Override
        public @NotNull String getFamilyName() {
            return "Remove trailing comma";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            var document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document != null) {
                TextRange range = descriptor.getTextRangeInElement();
                document.deleteString(range.getStartOffset(), range.getEndOffset());
            }
        }
    }
}
