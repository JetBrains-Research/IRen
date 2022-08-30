package org.jetbrains.iren;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.iren.config.InferenceStrategies;
import org.jetbrains.iren.services.IRenSuggestingService;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.storages.Vocabulary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.PyTokenTypes.*;
import static org.jetbrains.iren.utils.StringUtils.*;

public class PyLanguageSupporter extends LanguageSupporterBase {
    private static final List<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(PyNamedParameter.class, PyTargetExpression.class);
    private static final Collection<String> stopNames = List.of("self", "_");

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
    public @NotNull PsiElementVisitor createVariableVisitor(@NotNull ProblemsHolder holder) {
        return new PyVariableVisitor(holder);
    }

    @Override
    public boolean isStopName(@NotNull String name) {
        return stopNames.contains(name);
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
            for (int i = 0; i < elements.size(); i++) {
                PsiElement element = elements.get(i);
                if (usages.contains(element)) {
                    varIdxs.add(i);
                }
                if (element instanceof PsiWhiteSpace && element.getText().contains("\n")) {
                    tokens.add(NEW_LINE_TOKEN);
                    int newIndent = countIndent(file, element);
                    int diff = newIndent - indent;
                    if (diff != 0) {
                        indent = newIndent;
                        String indentToken = diff > 0 ? INDENT_TOKEN : DEDENT_TOKEN;
                        for (int j=0; j < diff; j++) tokens.add(indentToken);
                    }
                } else {
                    tokens.add(element.getText());
                }
            }
            return new Context<>(tokens, varIdxs);
        });
    }

    private static int countIndent(PsiFile file, PsiElement element) {
        return StringUtils.countMatches(PyIndentUtil.getElementIndent(element), PyIndentUtil.getIndentFromSettings(file));
    }

    protected List<PsiElement> lexFile(PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .forceIgnore(node -> node instanceof PsiComment)
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

    private final double FIRST_PROBABILITY_THRESHOLD = 0.5;

    @Override
    public boolean fastHighlighting(Project project, @NotNull PsiNameIdentifierOwner variable) {
        IRenSuggestingService suggestingService = IRenSuggestingService.getInstance(project);
        final Context.Statistics contextStatistics = suggestingService.getVariableContextStatistics(variable);
        if (contextStatistics.countsMean() < 1.) return false;
        @NotNull List<VarNamePrediction> predictions = suggestingService.suggestVariableName(project, variable, InferenceStrategies.NGRAM_ONLY);
        if (predictions.isEmpty()) return false;
        VarNamePrediction firstPrediction = predictions.get(0);
        final double firstProbability = firstPrediction.getProbability();
        final String firstName = firstPrediction.getName();
        return firstProbability > FIRST_PROBABILITY_THRESHOLD &&
                !Vocabulary.unknownCharacter.equals(firstName) &&
                !areSubtokensMatch(ReadAction.compute(variable::getName), varNamePredictions2set(predictions));
    }

    @Override
    public boolean slowHighlighting(Project project, @NotNull PsiNameIdentifierOwner variable) {
        return true;
    }
}
