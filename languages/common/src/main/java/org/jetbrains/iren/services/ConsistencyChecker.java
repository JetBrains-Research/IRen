package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class ConsistencyChecker implements Disposable {
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
                                   return !isRenamedVariable(SmartPointerManager.createPointer(variable)) &&
                                           !hasGoodPredictionList(variable);
                               }
                           }
                    );
    private final Set<SmartPsiElementPointer<PsiNameIdentifierOwner>> renamedVariables = new HashSet<>();

    public boolean isRenamedVariable(SmartPsiElementPointer<PsiNameIdentifierOwner> pointer) {
        return renamedVariables.contains(pointer);
    }

    public void rememberRenamedVariable(SmartPsiElementPointer<PsiNameIdentifierOwner> pointer) {
        renamedVariables.add(pointer);
    }

    public @NotNull Boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable) {
        Boolean res = null;
        try {
            res = inconsistentVariablesMap.get(variable);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return res != null && res;
    }


    public static boolean hasGoodPredictionList(@NotNull PsiNameIdentifierOwner variable) {
        @NotNull LinkedHashMap<String, Double> predictions = IRenSuggestingService.getInstance().suggestVariableName(variable);
        if (predictions.isEmpty()) return false;
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
        return unkIdx == 0 || varIdx < 5;
    }

    @Override
    public void dispose() {
        inconsistentVariablesMap.invalidateAll();
    }
}