package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.config.InferenceStrategies;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.jetbrains.iren.utils.LimitedTimeRunner.runForSomeTime;
import static org.jetbrains.iren.utils.StringUtils.areSubtokensMatch;


public class ConsistencyCheckerImpl implements ConsistencyChecker {
    private final int TIME_FOR_CHECKING = 2000;

    private ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ConsistencyChecker", 1);
    private final LoadingCache<PsiNameIdentifierOwner, Future<Boolean>> inconsistentVariablesMap =
            CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build(new CacheLoader<>() {
                               @Override
                               public @NotNull Future<Boolean> load(@NotNull PsiNameIdentifierOwner variable) {
                                   return executor.submit(() ->
                                           runForSomeTime(TIME_FOR_CHECKING, () ->
                                                           highlightByInspection(variable)));
                               }
                           }
                    );

    @Override
    public boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(variable.getLanguage());
        if (supporter == null ||
                RenameHistory.getInstance(variable.getProject()).isRenamedVariable(variable) ||
                supporter.excludeFromInspection(variable))
            return false;
        try {
            Future<Boolean> future = inconsistentVariablesMap.get(variable);
            if (future.isDone()) return future.get();
            return false;
        } catch (Exception ignore) {
            return false;
        }
    }

    static synchronized boolean highlightByInspection(@NotNull PsiNameIdentifierOwner variable) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(variable.getLanguage());
        Project project = ReadAction.compute(variable::getProject);
        return supporter.fastHighlighting(project, variable) && supporter.slowHighlighting(project, variable);
    }

    @Override
    public void dispose() {
        inconsistentVariablesMap.invalidateAll();
    }
}