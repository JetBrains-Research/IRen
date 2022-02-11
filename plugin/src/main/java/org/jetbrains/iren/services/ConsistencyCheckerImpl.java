package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.Vocabulary;

import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.iren.utils.LimitedTimeRunner.runForSomeTime;
import static org.jetbrains.iren.utils.StringUtils.isSubstringOfSuggestions;


public class ConsistencyCheckerImpl implements ConsistencyChecker {
    private final LoadingCache<PsiNameIdentifierOwner, Boolean> inconsistentVariablesMap =
            CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build(new CacheLoader<>() {
                               @Override
                               public Boolean load(@NotNull PsiNameIdentifierOwner variable) {
                                   return highlightByInspection(variable);
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

    static synchronized boolean highlightByInspection(@NotNull PsiNameIdentifierOwner variable) {
        final Context.Statistics contextStatistics = IRenSuggestingService.getInstance().getVariableContextStatistics(variable);
        if (contextStatistics.countsMean() < 1.) return false;
        @NotNull LinkedHashMap<String, Double> predictions = IRenSuggestingService.getInstance().suggestVariableName(variable);
        final Double firstProbability = predictions.values().stream().findFirst().orElse(0.);
        final String firstName = predictions.keySet().stream().findFirst().orElse(null);
        return firstProbability > 0.5 &&
                !Vocabulary.unknownCharacter.equals(firstName) &&
                !isSubstringOfSuggestions(variable.getName(), predictions.keySet());
    }

    @Override
    public void dispose() {
        inconsistentVariablesMap.invalidateAll();
    }
}