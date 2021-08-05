package org.jetbrains.id.names.suggesting.naturalize;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.psi.*;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.VarNamePrediction;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;
import org.jetbrains.id.names.suggesting.utils.PsiUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.id.names.suggesting.utils.PsiUtils.findReferences;
import static org.jetbrains.id.names.suggesting.utils.PsiUtils.isVariableOrReference;

public abstract class NaturalizeContributor implements VariableNamesContributor {
    public static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
    }

    @Override
    public int contribute(@NotNull PsiVariable variable, @NotNull List<VarNamePrediction> predictionList, boolean forgetContext) {
        IdNamesNGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return 0;
        }
        NaturalizeModelRunner naturalize = new NaturalizeModelRunner(
                modelRunner.getModel(),
                modelRunner.getVocabulary(),
                modelRunner.getRememberedIdentifiers());
        predictionList.addAll(naturalize.suggestNames(variable.getClass(), getContext(variable, false), forgetContext));
        return naturalize.getModelPriority();
    }

    @Override
    public @NotNull Pair<Double, Integer> getProbability(@NotNull PsiVariable variable, boolean forgetContext) {
        IdNamesNGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return new Pair<>(0.0, 0);
        }
        NaturalizeModelRunner naturalize = new NaturalizeModelRunner(
                modelRunner.getModel(),
                modelRunner.getVocabulary(),
                modelRunner.getRememberedIdentifiers());
        return naturalize.getProbability(getContext(variable, false), forgetContext);
    }

    public abstract @Nullable IdNamesNGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable);

    private static boolean isSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return SUPPORTED_TYPES.stream().anyMatch(type -> type.isInstance(identifierOwner));
    }

    private @NotNull Context getContext(@NotNull PsiVariable variable, boolean changeToUnknown) {
        PsiElement root = findRoot(variable);
        List<Integer> varIdxs = new ArrayList<>();
        List<PsiElement> elements = SyntaxTraverser.psiTraverser()
                .withRoot(root)
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(PsiUtils::shouldLex)
                .toList();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            if (isVariableOrReference(variable, element)) {
                varIdxs.add(i);
                tokens.add(changeToUnknown ? Vocabulary.unknownCharacter : element.getText());
            } else {
                tokens.add(element.getText());
            }
        }
        return new Context(tokens, varIdxs);
    }

    private @NotNull PsiElement findRoot(@NotNull PsiVariable variable) {
        PsiFile file = variable.getContainingFile();
        Stream<PsiReference> elementUsages = findReferences(variable, file);
        List<Set<PsiElement>> parents = Stream.concat(Stream.of(variable), elementUsages)
                .map(PsiUtils::getIdentifier)
                .filter(Objects::nonNull)
                .map(PsiUtils::getParents)
                .collect(Collectors.toList());
        Set<PsiElement> common = parents.remove(0);
        parents.forEach(common::retainAll);
        Optional<PsiElement> res = common.stream().findFirst();
        return res.orElse(file);
    }
}

class Context {
    final List<String> tokens;
    final List<Integer> varIdxs;

    public Context(List<String> tokens, List<Integer> varIdxs) {
        this.tokens = tokens;
        this.varIdxs = varIdxs;
    }

    public Context with(String name) {
        List<String> newTokens = new ArrayList<>(tokens);
        for (int idx : varIdxs) {
            newTokens.set(idx, name);
        }
        return new Context(newTokens, varIdxs);
    }
}
