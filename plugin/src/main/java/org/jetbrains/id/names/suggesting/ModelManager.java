package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.id.names.suggesting.utils.StringUtils.join;

public class ModelManager implements Disposable {
    private final Map<String, NGramModelRunner> myModelRunners = new HashMap<>();

    public static final Path MODELS_DIRECTORY = Paths.get(PathManager.getSystemPath(), "models");
    private static final Path GLOBAL_MODEL_DIRECTORY = MODELS_DIRECTORY.resolve("global");

    public static @NotNull ModelManager getInstance() {
        return ServiceManager.getService(ModelManager.class);
    }

    public static @NotNull Path getGlobalPath() {
        return GLOBAL_MODEL_DIRECTORY;
    }

    public static @NotNull Path getPath(@NotNull Project project) {
        return MODELS_DIRECTORY.resolve(project.getLocationHash());
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull Class<? extends VariableNamesContributor> name) {
        return myModelRunners.get(name.getSimpleName());
    }

    public void putModelRunner(@NotNull Class<? extends VariableNamesContributor> name, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(name.getSimpleName(), modelRunner);
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project) {
        NGramModelRunner modelRunner = myModelRunners.get(join(className, project));
        if (modelRunner != null) {
            return modelRunner;
        }
        return getModelRunner(className);
    }

    public void putModelRunner(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(join(className, project), modelRunner);
    }

    @Override
    public void dispose() {
        myModelRunners.clear();
    }
}
