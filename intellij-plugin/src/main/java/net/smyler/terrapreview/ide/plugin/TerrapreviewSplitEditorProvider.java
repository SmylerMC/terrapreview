package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider;

/**
 * Opens Terra-- configuration files in a split editor with a live Terrapreview preview,
 * the way Markdown files open with a preview.
 */
public final class TerrapreviewSplitEditorProvider extends TextEditorWithPreviewProvider {

    public TerrapreviewSplitEditorProvider() {
        super(new TerrapreviewPreviewFileEditorProvider());
    }

    @Override
    protected FileEditor createSplitEditor(TextEditor firstEditor, FileEditor secondEditor) {
        TextEditorWithPreview split = new TextEditorWithPreview(
                firstEditor, secondEditor, "terrapreview", TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW);
        return split;
    }
}
