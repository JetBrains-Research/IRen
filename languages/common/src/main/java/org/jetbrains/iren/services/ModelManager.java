package org.jetbrains.iren.services;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.api.ModelRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.iren.utils.PsiUtil.isIdeaProject;

public class ModelManager implements Disposable {
    public static final Path MODELS_DIRECTORY = Paths.get(PathManager.getSystemPath(), "models");
    public static final String INTELLIJ_NAME = "intellij";
    public static final String INTELLIJ_MODEL_VERSION = "1";

    private final Map<String, ModelRunner> myModelRunners = new HashMap<>();

    public static @NotNull ModelManager getInstance() {
        return ApplicationManager.getApplication().getService(ModelManager.class);
    }

    public static @NotNull Path getPath(@NotNull String name) {
        return MODELS_DIRECTORY.resolve(name);
    }

    public static @NotNull String getName(@NotNull Project project,
                                          @Nullable Language language) {
        return (isIdeaProject(project) ?
                String.join("_", INTELLIJ_NAME, INTELLIJ_MODEL_VERSION) :
                String.join("_", project.getName(), project.getLocationHash())
        ) + (language == null ? "" : "_" + language.getID());
    }

    public @Nullable ModelRunner getModelRunner(@NotNull String name) {
        return myModelRunners.get(name);
    }

    public void putModelRunner(@NotNull String name, @NotNull ModelRunner modelRunner) {
        myModelRunners.put(name, modelRunner);
    }

    public void removeModelRunner(@NotNull String name) {
        myModelRunners.remove(name);
    }

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

    public boolean containsIntellijModel() {
        return myModelRunners.keySet().stream().anyMatch(name ->
                name.startsWith(INTELLIJ_NAME + "_" + INTELLIJ_MODEL_VERSION)
        );
    }
}
