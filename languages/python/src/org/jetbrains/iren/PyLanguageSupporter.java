package org.jetbrains.iren;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.PyTokenTypes.*;
import static org.jetbrains.iren.utils.StringUtils.*;

public class PyLanguageSupporter extends LanguageSupporterBase {
    private static final List<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(PyNamedParameter.class, PyTargetExpression.class);
    private static final Collection<String> stopNames = List.of("self", "_");
    private final PyDOBFTokenizer tokenizer = new PyDOBFTokenizer();

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
            return INTEGERS_TO_LEAVE.contains(text) ? text : NUMBER_TOKEN;
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
    public @NotNull PsiElementVisitor createVariableVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PyVariableVisitor(holder, isOnTheFly);
    }

    @Override
    public boolean isStopName(@NotNull String name) {
        return stopNames.contains(name);
    }

    @Override
    protected DOBFTokenizer getTokenizer() {
        return tokenizer;
    }

    @Override
    protected boolean isStringElement(PsiElement element) {
        IElementType type = element.getNode().getElementType();
        return STRING_NODES.contains(type) ||
                FSTRING_TEXT_TOKENS.contains(type);
    }

    @Override
    public boolean excludeFromInspection(@NotNull PsiNameIdentifierOwner variable) {
        RefactoringSupportProvider provider = LanguageRefactoringSupport.INSTANCE.forContext(variable);
        return provider != null && !provider.isInplaceRenameAvailable(variable, null) ||
                variable.getChildren().length != 0 ||
                variable.getParent() instanceof PyImportElement ||
                super.excludeFromInspection(variable);
    }

    @Override
    public @Nullable Context<String> getDOBFContext(@NotNull PsiNameIdentifierOwner variable) {
        return ReadAction.compute(() -> {
            PsiFile file = variable.getContainingFile();
            Collection<PsiElement> usages = findUsages(variable, file);
            if (file == null) return null;
            List<Integer> varIdxs = new ArrayList<>();
            List<PsiElement> elements = lexFile(file);
            List<String> tokens = new ArrayList<>();
            int indent = 0;
            for (PsiElement element : elements) {
                String text = element.getText();
                if (element instanceof PsiWhiteSpace) {
                    if (!text.contains("\n")) continue;
                    indent = addNewLineAndIndentation(file, tokens, indent, element);
                } else if (StringUtils.isNotBlank(text)) {
                    if (usages.contains(element)) {
                        varIdxs.add(tokens.size());
                    }
                    tokens.add(processDOBFToken(element));
                }
            }
            return new Context<>(tokens, varIdxs);
        });
    }

    private static int addNewLineAndIndentation(PsiFile file, List<String> tokens, int indent, PsiElement element) {
        int newIndent = countIndent(file, element);
        int diff = newIndent - indent;
        if (diff != 0) {
            tokens.add(NEW_LINE_TOKEN);
            indent = newIndent;
            String indentToken = diff > 0 ? INDENT_TOKEN : DEDENT_TOKEN;
            for (int j = 0; j < Math.abs(diff); j++) tokens.add(indentToken);
        } else {
            PsiElement prevSibling = element.getPrevSibling();
            if ((prevSibling instanceof PyStatement ||
                    prevSibling instanceof PyDecorator ||
                    prevSibling instanceof PyDecoratorList) &&
                    tokens.size() > 0 &&
                    !INDENT_TOKENS.contains(tokens.get(tokens.size() - 1)))
                tokens.add(NEW_LINE_TOKEN);
        }
        return indent;
    }

    private static int countIndent(PsiFile file, PsiElement element) {
        return StringUtils.countMatches(PyIndentUtil.getElementIndent(element), PyIndentUtil.getIndentFromSettings(file));
    }

    protected List<PsiElement> lexFile(PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .forceIgnore(node -> node instanceof PsiComment || node.getNode().getElementType() == DOCSTRING)
                .filter(this::isLeaf)
                .toList();
    }

    @Override
    public boolean isColliding(@NotNull PsiElement element, @NotNull String newName) {
        return super.isColliding(element, newName) ||
                element instanceof PyParameter && ReadAction.compute(() -> isCollidingWithParameter((PyParameter) element, newName));
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

    @Override
    public boolean dobfReady() {
        return true;
    }
}
