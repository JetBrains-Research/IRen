package org.jetbrains.iren.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelRunner;

public interface ModelManager extends Disposable {
    static @NotNull ModelManager getInstance() {
        return ApplicationManager.getApplication().getService(ModelManager.class);
    }

    @Nullable ModelRunner getModelRunner(@NotNull String name);

    void putModelRunner(@NotNull String name, @NotNull ModelRunner modelRunner);

    void removeModelRunner(@NotNull String name);

    void removeProjectModelRunners(@NotNull Project project);

    boolean containsIntellijModel();

    void forgetFileIfNeeded(@NotNull ModelRunner modelRunner, @NotNull PsiFile newFile);
}
