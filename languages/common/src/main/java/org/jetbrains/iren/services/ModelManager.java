package org.jetbrains.iren.services;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ngram.NGramModelRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ModelManager implements Disposable {
    private final Map<String, NGramModelRunner> myModelRunners = new HashMap<>();

    public static final Path MODELS_DIRECTORY = Paths.get(PathManager.getSystemPath(), "models");

    public static @NotNull ModelManager getInstance() {
        return ApplicationManager.getApplication().getService(ModelManager.class);
    }

    public static @NotNull Path getPath(@NotNull String name) {
        return MODELS_DIRECTORY.resolve(name);
    }

    public static @NotNull String getName(@Nullable Project project,
                                          @Nullable Language language) {
        return (project != null ? project.getName() + "_" + project.getLocationHash() + "_" : "") +
                (language != null ? language.getID() : "");
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull String name) {
        return myModelRunners.get(name);
    }

    public void putModelRunner(@NotNull String name, @NotNull NGramModelRunner modelRunner) {
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

    public synchronized void forgetFileIfNeeded(@NotNull NGramModelRunner modelRunner, @NotNull PsiFile newFile) {
        String modelKey = modelRunner.toString();
        PsiFile oldFile = fileMap.get(modelKey);
        if (newFile != oldFile) {
            if (oldFile != null) modelRunner.learnPsiFile(oldFile);
            modelRunner.forgetPsiFile(newFile);
            fileMap.put(modelKey, newFile);
        }
    }
}
