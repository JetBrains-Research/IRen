package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.psi.*;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;
import org.jetbrains.id.names.suggesting.storages.Context;
import org.jetbrains.id.names.suggesting.storages.IntContext;
import org.jetbrains.id.names.suggesting.storages.VarNamePrediction;
import org.jetbrains.id.names.suggesting.utils.PsiUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.id.names.suggesting.utils.PsiUtils.*;

public abstract class NGramVariableNamesContributor implements VariableNamesContributor {
    public static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
    }

    @Override
    public int contribute(@NotNull PsiVariable variable, @NotNull List<VarNamePrediction> predictionList) {
        NGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return 0;
        }
        @NotNull IntContext intContext = IntContext.fromContext(getContext(variable, false), modelRunner.getVocabulary());
        PsiFile file = variable.getContainingFile();
        if (this.forgetFile()) {
            modelRunner.forgetPsiFile(file);
        } else if (this.forgetContext()) {
            modelRunner.forgetContext(intContext);
        }

        predictionList.addAll(modelRunner.suggestNames(variable.getClass(), intContext));

        if (this.forgetFile()) {
            modelRunner.learnPsiFile(file);
        } else if (this.forgetContext()) {
            modelRunner.learnContext(intContext);
        }
        return modelRunner.getModelPriority();
    }

    protected abstract boolean forgetFile();

    protected abstract boolean forgetContext();

    @Override
    public @NotNull Pair<Double, Integer> getProbability(@NotNull PsiVariable variable) {
        NGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return new Pair<>(0.0, 0);
        }
        @NotNull IntContext intContext = IntContext.fromContext(getContext(variable, false), modelRunner.getVocabulary());
        PsiFile file = variable.getContainingFile();
        if (this.forgetFile()) {
            modelRunner.forgetPsiFile(file);
        } else if (this.forgetContext()) {
            modelRunner.forgetContext(intContext);
        }

        @NotNull Pair<Double, Integer> prob = modelRunner.getProbability(intContext);
        if (this.forgetFile()) {
            modelRunner.learnPsiFile(file);
        } else if (this.forgetContext()) {
            modelRunner.learnContext(intContext);
        }
        return prob;
    }

    public abstract @Nullable NGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable);

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
                tokens.add(processToken(element));
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

