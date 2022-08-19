package org.jetbrains.iren.services;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelRunner;
import org.jetbrains.iren.utils.ModelUtils;

import java.util.HashMap;
import java.util.Map;

public class NGramModelManagerImpl implements NGramModelManager {
    private final Map<String, ModelRunner> myModelRunners = new HashMap<>();

    @Override
    public @Nullable ModelRunner get(@NotNull String name) {
        return myModelRunners.get(name);
    }

    @Override
    public void put(@NotNull String name, @NotNull ModelRunner modelRunner) {
        myModelRunners.put(name, modelRunner);
    }

    @Override
    public void remove(@NotNull String name) {
        myModelRunners.remove(name);
    }

    @Override
    public void removeProjectModelRunners(@NotNull Project project) {
        String name = project.getName() + "_" + project.getLocationHash();
        myModelRunners.entrySet().removeIf(entry -> entry.getKey().contains(name));
    }

    @Override
    public void dispose() {
        myModelRunners.clear();
    }

    private final Map<String, PsiFile> fileMap = new HashMap<>();

    public synchronized void forgetFileIfNeeded(@NotNull ModelRunner modelRunner, @NotNull PsiFile newFile) {
        String modelKey = modelRunner.toString();
        PsiFile oldFile = fileMap.get(modelKey);
        if (newFile != oldFile) {
            if (oldFile != null) modelRunner.learnPsiFile(oldFile);
            modelRunner.forgetPsiFile(newFile);
            fileMap.put(modelKey, newFile);
        }
    }

    @Override
    public boolean containsIntellijModel() {
        return myModelRunners.keySet().stream().anyMatch(name ->
                name.startsWith(ModelUtils.INTELLIJ_NAME)
        );
    }
}
