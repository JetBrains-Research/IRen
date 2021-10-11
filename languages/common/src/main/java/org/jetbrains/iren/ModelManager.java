package org.jetbrains.iren;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.impl.NGramModelRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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

    public void deleteProjectModelRunners(@NotNull Project project) {
        String name = project.getName() + "_" + project.getLocationHash();
        for (String key : myModelRunners.keySet()) {
            if (key.contains(name)) {
                myModelRunners.remove(key);
            }
        }
    }

    @Override
    public void dispose() {
        myModelRunners.clear();
    }

    private final Map<String, Consumer<String>> consumerMap = new HashMap<>();

    public synchronized void invokeLater(@NotNull Project project, Consumer<String> consumer) {
        invoke(project, null);
        @NotNull String projectHash = project.getLocationHash();
        if (consumerMap.containsKey(projectHash)) System.out.println("invokeLater bug");
        consumerMap.put(projectHash, consumer);
    }

    public void invoke(@NotNull Project project, String name) {
        Consumer<String> consumer = consumerMap.remove(project.getLocationHash());
        if (consumer != null) {
            try {
                consumer.accept(name);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
