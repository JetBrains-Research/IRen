package org.jetbrains.iren.rename;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenSuggestingService;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;

public class MyVariableInplaceRenameHandler extends VariableInplaceRenameHandler {
    @Override
    public @Nullable InplaceRefactoring doRename(@NotNull PsiElement elementToRename,
                                                 @NotNull Editor editor,
                                                 @Nullable DataContext dataContext) {
        if (dataContext == null) return null;
        @Nullable Language language = dataContext.getData(LangDataKeys.LANGUAGE);
        if (language != null && language.is(JavaLanguage.INSTANCE) &&
                ModelStatsService.getInstance().isUsable(ProjectVariableNamesContributor.class, editor.getProject())) {
            @Nullable PsiVariable variable = (PsiVariable) elementToRename;
            MyMemberInplaceRenamer renamer = createMyRenamer(variable, editor);
            boolean startedRename = renamer != null && renamer.performInplaceRefactoring(
                    IRenSuggestingService.getInstance().suggestVariableName(variable));

            if (!startedRename) {
                performDialogRename(variable, editor, dataContext, renamer != null ? variable.getName() : null);
            }
            return renamer;
        }
        return super.doRename(elementToRename, editor, dataContext);
    }

    protected @Nullable MyMemberInplaceRenamer createMyRenamer(@NotNull PsiElement elementToRename, @NotNull Editor editor) {
        return new MyMemberInplaceRenamer((PsiNamedElement) elementToRename, editor);
    }
}
