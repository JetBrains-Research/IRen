package org.jetbrains.iren.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

public interface ConsistencyChecker extends Disposable {
    static ConsistencyChecker getInstance(@NotNull Project project) {
        return project.getService(ConsistencyChecker.class);
    }

    boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable);
}
