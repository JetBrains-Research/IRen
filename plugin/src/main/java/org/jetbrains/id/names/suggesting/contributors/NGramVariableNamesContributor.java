package org.jetbrains.id.names.suggesting.contributors;

import com.google.common.collect.Lists;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.VarNamePrediction;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;
import org.jetbrains.id.names.suggesting.utils.PsiUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;
import static org.jetbrains.id.names.suggesting.utils.PsiUtils.findReferences;
import static org.jetbrains.id.names.suggesting.utils.PsiUtils.isVariableOrReference;

public abstract class NGramVariableNamesContributor implements VariableNamesContributor {
    public static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
    }

    private int modelOrder;

    @Override
    public int contribute(@NotNull PsiVariable variable, @NotNull List<VarNamePrediction> predictionList, boolean forgetUsages) {
        IdNamesNGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return 0;
        }
        modelOrder = modelRunner.getOrder();
        predictionList.addAll(modelRunner.suggestNames(variable.getClass(), findUsageNGrams(variable), forgetUsages));
        return modelRunner.getModelPriority();
    }

    @Override
    public @NotNull Pair<Double, Integer> getProbability(@NotNull PsiVariable variable, boolean forgetUsages) {
        IdNamesNGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return new Pair<>(0.0, 0);
        }
        modelOrder = modelRunner.getOrder();
        return modelRunner.getProbability(findUsageNGrams(variable), forgetUsages);
    }

    public abstract @Nullable IdNamesNGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable);

    private static boolean isSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return SUPPORTED_TYPES.stream().anyMatch(type -> type.isInstance(identifierOwner));
    }

    private List<List<String>> findUsageNGrams(PsiVariable variable) {
        Stream<PsiReference> elementUsages = findReferences(variable, variable.getContainingFile());
        return Stream.concat(Stream.of(variable), elementUsages)
                .map(PsiUtils::getIdentifier)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PsiElement::getTextOffset))
                .map(identifier -> getNGram(identifier, variable))
                .collect(Collectors.toList());
    }

    private List<String> getNGram(@NotNull PsiElement element, @NotNull PsiVariable variable) {
        int order = modelOrder;
        final List<String> tokens = new ArrayList<>();
        for (PsiElement token : SyntaxTraverser
                .revPsiTraverser()
                .withRoot(element.getContainingFile())
                .onRange(new TextRange(0, max(0, element.getTextOffset())))
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(PsiUtils::shouldLex)) {
            tokens.add(processToken(token, variable));
            if (--order < 1) {
                break;
            }
        }
        return Lists.reverse(tokens);
    }

    public static String processToken(@NotNull PsiElement token, @NotNull PsiVariable variable) {
        if (isVariableOrReference(variable, token)) {
            return Vocabulary.unknownCharacter;
        }
        return token.getText();
    }
}
