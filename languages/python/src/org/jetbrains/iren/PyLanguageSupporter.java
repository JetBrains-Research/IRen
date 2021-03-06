package org.jetbrains.iren;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.PyTokenTypes.*;
import static org.jetbrains.iren.utils.StringUtils.*;

public class PyLanguageSupporter extends LanguageSupporterBase {
    private static final List<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(PyNamedParameter.class, PyTargetExpression.class);
    private static final Collection<String> stopNames = List.of("self");
    @Override
    public @NotNull Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @Override
    public @NotNull FileType getFileType() {
        return PythonFileType.INSTANCE;
    }

    @Override
    public @NotNull IElementType getIdentifierType() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull Collection<Class<? extends PsiNameIdentifierOwner>> getVariableClasses() {
        return variableClasses;
    }

    @Override
    public void removeHandlers() {
        RenameHandler.EP_NAME.getPoint().unregisterExtension(VariableInplaceRenameHandler.class);
    }

    @Override
    protected String processLiteral(@NotNull PsiElement token, @NotNull String text) {
        @NotNull IElementType tokenType = token.getNode().getElementType();
        if (STRING_NODES.contains(tokenType) || FSTRING_TEXT_TOKENS.contains(tokenType)) {
            return STRING_TOKEN;
        } else if (NUMERIC_LITERALS.contains(tokenType)) {
            return IntegersToLeave.contains(text) ? text : NUMBER_TOKEN;
        }
        return null;
    }

    @Override
    protected Collection<Class<? extends PsiNameIdentifierOwner>> getFunctionAndClassPsi() {
        return List.of(PyFunction.class, PyClass.class);
    }

    @Override
    protected PsiElement resolveReference(@NotNull PsiElement element) {
        return element instanceof PyReferenceExpression ? ((PyReferenceExpression) element).getReference().resolve() : null;
    }

    @Override
    public boolean isIdentifier(@Nullable PsiElement token) {
        return token != null && token.getNode().getElementType() == IDENTIFIER;
    }

    @Override
    public @NotNull PsiElementVisitor createVariableVisitor(@NotNull ProblemsHolder holder) {
        return new PyVariableVisitor(holder);
    }

    @Override
    public boolean isStopName(@NotNull String name) {
        return stopNames.contains(name);
    }

    @Override
    public boolean isInplaceRenameAvailable(PsiNamedElement elementToRename) {
        return new PyRefactoringProvider().isInplaceRenameAvailable(elementToRename, null);
    }

    @Override
    public boolean isColliding(@NotNull PsiElement element, @NotNull String newName) {
        return super.isColliding(element, newName) ||
                element instanceof PyParameter && isCollidingWithParameter((PyParameter) element, newName);
    }

    /**
     * Checks if a parameter with the newName already exists
     */
    private boolean isCollidingWithParameter(@NotNull PyParameter element, @NotNull String newName) {
        final PsiElement parent = element.getParent();
        return parent instanceof PyParameterList && Arrays.stream(((PyParameterList) parent).getParameters()).anyMatch(parameter -> {
            final PyNamedParameter namedParameter = parameter.getAsNamed();
            return namedParameter != null && newName.equals(namedParameter.getName());
        });
    }
}
