package org.jetbrains.iren.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.services.RenameHistory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static org.jetbrains.iren.rename.IRenVariableInplaceRenamer.rememberNameAfterRefactoring;
import static org.jetbrains.iren.utils.RenameUtils.addIRenPredictionsIfPossible;

public class IRenMemberInplaceRenamer extends MemberInplaceRenamer {
    private final LinkedHashMap<String, Double> myNameProbabilities = new LinkedHashMap<>();
    private final LinkedHashMap<String, ModelType> myModelTypes = new LinkedHashMap<>();
    private @Nullable String variableHash = null;

    public IRenMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @NotNull Editor editor) {
        this(elementToRename, null, editor);
    }

    public IRenMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @Nullable PsiElement substituted, @NotNull Editor editor) {
        super(elementToRename, substituted, editor);
    }

    public IRenMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename,
                                    @Nullable PsiElement substituted,
                                    @NotNull Editor editor,
                                    @Nullable String initialName,
                                    @Nullable String oldName) {
        super(elementToRename, substituted, editor, initialName, oldName);
    }

    @Override
    public boolean performInplaceRefactoring(@Nullable LinkedHashSet<String> nameSuggestions) {
        if (nameSuggestions == null) nameSuggestions = new LinkedHashSet<>();
        variableHash = RenameHistory.getInstance(myProject).getVariableHash(myElementToRename, false);
        return super.performInplaceRefactoring(nameSuggestions);
    }

    @Override
    protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
        addIRenPredictionsIfPossible(myNameSuggestions,
                myElementToRename,
                selectedElement,
                myNameProbabilities,
                myModelTypes);
        return new IRenLookupExpression(getInitialName(),
                myNameSuggestions,
                myElementToRename,
                selectedElement,
                shouldSelectAll(),
                myAdvertisementText,
                myNameProbabilities,
                myModelTypes);
    }

    @Override
    public void afterTemplateStart() {
        rememberNameAfterRefactoring(myProject, myEditor, variableHash);
    }
}
