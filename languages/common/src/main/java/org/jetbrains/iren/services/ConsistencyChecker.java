package org.jetbrains.iren.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

public interface ConsistencyChecker extends Disposable {
    static ConsistencyChecker getInstance() {
        return ApplicationManager.getApplication().getService(ConsistencyChecker.class);
    }

    @NotNull Boolean isInconsistent(@NotNull PsiNameIdentifierOwner variable);
}
