package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.LanguageSupporter;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.iren.utils.LimitedTimeRunner.runForSomeTime;


public class ConsistencyCheckerImpl implements ConsistencyChecker {
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

    @Override
    public @NotNull Boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable) {
        if (RenameHistory.getInstance(variable.getProject()).isRenamedVariable(variable) ||
                LanguageSupporter.getInstance(variable.getLanguage()).excludeFromInspection(variable))
            return false;
        Boolean res = runForSomeTime(300, () -> {
            try {
                return inconsistentVariablesMap.get(variable);
            } catch (Exception ignore) {
                return false;
            }
        });
        return res != null && res;
    }

    static boolean isBetterNamesSuggested(@NotNull PsiNameIdentifierOwner variable) {
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