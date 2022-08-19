package org.jetbrains.iren.rename;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.config.InferenceStrategies;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.services.RenameHistory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static org.jetbrains.iren.utils.RenameUtils.addIRenPredictionsIfPossible;
import static org.jetbrains.iren.utils.RenameUtils.notTypoRename;

public class IRenVariableInplaceRenamer extends VariableInplaceRenamer {
    private final LinkedHashMap<String, Double> myNameProbabilities = new LinkedHashMap<>();
    private final LinkedHashMap<String, ModelType> myModelTypes = new LinkedHashMap<>();
    private @Nullable String variableHash = null;

    public IRenVariableInplaceRenamer(@NotNull PsiNamedElement elementToRename, @NotNull Editor editor) {
        super(elementToRename, editor);
    }

    public IRenVariableInplaceRenamer(@Nullable PsiNamedElement elementToRename, @NotNull Editor editor, @NotNull Project project) {
        super(elementToRename, editor, project);
    }

    public IRenVariableInplaceRenamer(@Nullable PsiNamedElement elementToRename, @NotNull Editor editor, @NotNull Project project, @Nullable String initialName, @Nullable String oldName) {
        super(elementToRename, editor, project, initialName, oldName);
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
                myModelTypes,
                notTypoRename() ? InferenceStrategies.ALL : InferenceStrategies.DEFAULT_ONLY);
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
    protected void afterTemplateStart() {
        rememberNameAfterRefactoring(myProject, myEditor, variableHash);
    }

    public static void rememberNameAfterRefactoring(@NotNull Project project,
                                                    @Nullable Editor editor,
                                                    @Nullable String scopeHash) {
        if (editor == null || scopeHash == null) return;
        TemplateState state = TemplateManagerImpl.getTemplateState(editor);
        if (state != null) state.addTemplateStateListener(new TemplateEditingAdapter() {
            @Override
            public void beforeTemplateFinished(@NotNull TemplateState state, Template template) {
                final TextResult value = state.getVariableValue(PRIMARY_VARIABLE_NAME);
                String insertedName = value != null ? value.toString().trim() : null;
                if (insertedName == null) return;
                RenameHistory.getInstance(project).rememberVariableName(scopeHash, insertedName);
            }
        });
    }
}
