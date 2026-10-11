package net.smyler.terrapreview.ide.plugin;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

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
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!TerraConfigurationFileRecognizer.isConfigurationFile(file.getVirtualFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        CharSequence contents = file.getViewProvider().getContents();
        if (contents == null || !contents.textContains(',')) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        for (TerraTrailingCommaScanner.TrailingComma comma : TerraTrailingCommaScanner.scan(contents.toString())) {
            holder.registerProblem(
                    file, TextRange.create(comma.start(), comma.end()), PROBLEM_DESCRIPTION, new RemoveTrailingCommaFix());
        }
        return PsiElementVisitor.EMPTY_VISITOR;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
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
