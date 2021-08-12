package org.jetbrains.id.names.suggesting.api;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;

public abstract class SuggestIdNamesIntentionBase<T extends PsiNameIdentifierOwner> extends BaseElementAtCaretIntentionAction {
    @Override
    public @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    protected boolean checkFile(@NotNull PsiFile file) {
        return file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE) && super.checkFile(file);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        final T identifierOwner = getIdentifierOwner(element);
        return identifierOwner != null && identifierOwner.isValid()
                && IdNamesSuggestingModelManager.getInstance().isSomethingLoaded();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        final T identifierOwner = getIdentifierOwner(element);
        assert identifierOwner != null;
        assert identifierOwner.isValid() : "Invalid element:" + identifierOwner;
        processIntention(project, editor, identifierOwner);
    }

    protected abstract @Nullable T getIdentifierOwner(@Nullable PsiElement element);

    protected abstract void processIntention(@NotNull Project project, Editor editor, @NotNull T identifierOwner);
}
