package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class ConsistencyChecker implements Disposable {
    public static ConsistencyChecker getInstance() {
        return ApplicationManager.getApplication().getService(ConsistencyChecker.class);
    }

    private final LoadingCache<PsiNameIdentifierOwner, Boolean> storage =
            CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build(new CacheLoader<>() {
                               @Override
                               public Boolean load(@NotNull PsiNameIdentifierOwner variable) {
                                   return !hasGoodPredictionList(variable);
                               }
                           }
                    );

    public @NotNull Boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable) {
        Boolean res = null;
        try {
            res = storage.get(variable);
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
        storage.invalidateAll();
    }
}