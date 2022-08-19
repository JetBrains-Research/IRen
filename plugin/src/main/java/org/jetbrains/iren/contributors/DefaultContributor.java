package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.VariableNamesContributor;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultContributor implements VariableNamesContributor {
//     TODO: mb it was not the best idea to move default NameSuggestionProvider to contributors...
//      Pros: if I add a flag not to suggest known name variants, it can be used in inspection :)
//      Cons: code became more bug prone :(
    @Override
    public int contribute(@NotNull PsiNameIdentifierOwner variable, @Nullable PsiElement selectedElement, @NotNull List<VarNamePrediction> predictionList) {
        Set<String> defaultSuggestions = new LinkedHashSet<>();
        NameSuggestionProvider.suggestNames(variable, selectedElement, defaultSuggestions);
        predictionList.addAll(
                defaultSuggestions.stream()
                        .map(name -> new VarNamePrediction(name, 0, ModelType.DEFAULT))
                        .collect(Collectors.toList())
        );
        return 0;
    }

    @Override
    public @NotNull Pair<Double, Integer> getProbability(@NotNull PsiNameIdentifierOwner variable) {
        return new Pair<>(.0, 0);
    }

    @Override
    public @NotNull ModelType getModelType() {
        return ModelType.DEFAULT;
    }
}
