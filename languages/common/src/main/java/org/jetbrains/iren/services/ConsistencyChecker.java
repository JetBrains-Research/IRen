package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class ConsistencyChecker implements Disposable {
    public static Key<Collection<String>> rememberedNames = Key.create("names");
    public static ConsistencyChecker getInstance() {
        return ApplicationManager.getApplication().getService(ConsistencyChecker.class);
    }

    private final LoadingCache<PsiNameIdentifierOwner, Boolean> inconsistentVariablesMap =
            CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build(new CacheLoader<>() {
                               @Override
                               public Boolean load(@NotNull PsiNameIdentifierOwner variable) {
                                   return isBetterNamesSuggested(variable);
                               }
                           }
                    );

    public static void rememberVariableName(@NotNull PsiElement elementToStoreNames, @NotNull String insertedName) {
        @Nullable Collection<String> names = elementToStoreNames.getUserData(ConsistencyChecker.rememberedNames);
        if (names == null) {
            names = new SmartHashSet<>();
            elementToStoreNames.putUserData(ConsistencyChecker.rememberedNames, names);
        }
        names.add(insertedName);
    }

    public static boolean isRenamedVariable(@NotNull PsiNameIdentifierOwner variable) {
        final PsiElement nameIdentifier = variable.getNameIdentifier();
        if (nameIdentifier == null) return false;
        final PsiElement elementToStoreNames = getElementToStoreNames(variable);
        if (elementToStoreNames == null) return false;
        final Collection<String> names = elementToStoreNames.getUserData(rememberedNames);
        return names != null && names.contains(nameIdentifier.getText());
    }

    public static @Nullable PsiElement getElementToStoreNames(@NotNull PsiNamedElement variable) {
        return variable.getParent();
    }

    public @NotNull Boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable) {
        if (isRenamedVariable(variable)) return false;
        Boolean res = null;
        try {
            res = inconsistentVariablesMap.get(variable);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return res != null && res;
    }


    public static boolean isBetterNamesSuggested(@NotNull PsiNameIdentifierOwner variable) {
        @NotNull LinkedHashMap<String, Double> predictions = IRenSuggestingService.getInstance().suggestVariableName(variable);
        if (predictions.values().stream().findFirst().orElse(0.) < 0.5) return false;
        int varIdx = 100;
        int unkIdx = 100;
        int i = 0;
        @Nullable String varName = variable.getName();
        for (String name : predictions.keySet()) {
            if (Objects.equals(varName, name)) {
                varIdx = i;
            } else if (Objects.equals(Vocabulary.unknownCharacter, name)) {
                unkIdx = i;
            }
            i++;
        }
        return unkIdx != 0 && varIdx > 10;
    }

    @Override
    public void dispose() {
        inconsistentVariablesMap.invalidateAll();
    }
}