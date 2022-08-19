package org.jetbrains.iren.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelRunner;

public interface NGramModelManager extends Disposable {
    static @NotNull NGramModelManager getInstance(@NotNull Project project) {
        return project.getService(NGramModelManager.class);
    }

    @Nullable ModelRunner get(@NotNull String name);

    void put(@NotNull String name, @NotNull ModelRunner modelRunner);

    void remove(@NotNull String name);

    void removeProjectModelRunners(@NotNull Project project);

    boolean containsIntellijModel();

    void forgetFileIfNeeded(@NotNull ModelRunner modelRunner, @NotNull PsiFile newFile);
}
