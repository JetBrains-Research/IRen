package org.jetbrains.iren;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.api.VariableNamesContributor;
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

    public static String getName(@Nullable Class<? extends VariableNamesContributor> clazz,
                                 @Nullable Project project,
                                 @Nullable Language language) {
        return (clazz != null ? clazz.getSimpleName() + "_" : "") +
                (project != null ? project.getName() + "_" + project.getLocationHash() + "_" : "") +
                (language != null ? language.getID() : "");
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull String name) {
        return myModelRunners.get(name);
    }

    public void putModelRunner(@NotNull String name, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(name, modelRunner);
    }

    @Override
    public void dispose() {
        myModelRunners.clear();
    }

    private final Map<String, Consumer<String>> consumerMap = new HashMap<>();

    public void invokeLater(@NotNull Project project, Consumer<String> consumer) {
        invoke(project, null);
        consumerMap.put(project.getLocationHash(), consumer);
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
