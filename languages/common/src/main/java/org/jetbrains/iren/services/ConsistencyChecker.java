package org.jetbrains.iren.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.LanguageSupporter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.iren.utils.LimitedTimeRunner.runForSomeTime;

public class ConsistencyChecker implements Disposable {
    public static ConsistencyChecker getInstance(@NotNull Project project) {
        return project.getService(ConsistencyChecker.class);
    }

    private final int TIME_FOR_CHECKING = 2000;

    private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ConsistencyChecker", 1);
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

    public boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable, boolean isOnTheFly) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(variable.getLanguage());
        if (supporter == null) return false;
        if (ApplicationManager.getApplication().isUnitTestMode()) return !supporter.excludeFromInspection(variable);
        if (RenameHistory.getInstance(variable.getProject()).isRenamedVariable(variable) ||
                supporter.excludeFromInspection(variable)) {
            return false;
        }
        try {
            Future<Boolean> future = isOnTheFly ?
                    inconsistentVariablesMap.get(variable) :
                    inconsistentVariablesMap.getIfPresent(variable);
//            Only cached variables will be highlighted in batch mode of the inspection
            return future != null && future.isDone() && future.get();
        } catch (Exception ignore) {
            return false;
        }
    }

    static synchronized boolean highlightByInspection(@NotNull PsiNameIdentifierOwner variable) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(variable.getLanguage());
        if (supporter == null) return false;
        Project project = ReadAction.compute(variable::getProject);
        return supporter.fastHighlighting(project, variable) && supporter.slowHighlighting(project, variable);
    }

    @Override
    public void dispose() {
        inconsistentVariablesMap.invalidateAll();
    }
}
