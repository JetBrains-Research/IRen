package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.FileVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;
import org.jetbrains.id.names.suggesting.utils.NotificationsUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class IdNamesSuggestingModelManager {
    private final Map<String, Instant> myLoadingTime = new HashMap<>();
    private final Map<String, NGramModelRunner> myModelRunners = new HashMap<>();

    public static final Path MODELS_DIRECTORY = Paths.get(PathManager.getSystemPath(), "models");
    private static final Path GLOBAL_MODEL_DIRECTORY = MODELS_DIRECTORY.resolve("global");
    public static Duration modelsLifetime = Duration.of(1, ChronoUnit.HOURS);

    public IdNamesSuggestingModelManager() {
        setLoaded(FileVariableNamesContributor.class, true);
    }

    public static @NotNull IdNamesSuggestingModelManager getInstance() {
        return ServiceManager.getService(IdNamesSuggestingModelManager.class);
    }

    public static @NotNull Path getGlobalPath() {
        return GLOBAL_MODEL_DIRECTORY;
    }

    public static @NotNull Path getPath(@NotNull Project project) {
        return MODELS_DIRECTORY.resolve(project.getLocationHash());
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull Class<? extends VariableNamesContributor> name) {
        return myModelRunners.get(name.getName());
    }

    public void putModelRunner(@NotNull Class<? extends VariableNamesContributor> name, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(name.getName(), modelRunner);
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project) {
        NGramModelRunner modelRunner = myModelRunners.get(join(className, project));
        if (modelRunner != null) {
            return modelRunner;
        }
//        TODO: check if it is working correctly. Is it worth to implement that with the help of listeners?
        if (!Duration.between(whenLoaded(className, project), Instant.now()).minus(modelsLifetime).isNegative()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("training.task.title")) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText(IdNamesSuggestingBundle.message("training.progress.indicator.text", project.getName()));
                    ReadAction.nonBlocking(() -> trainProjectNGramModel(project, indicator, true))
                            .inSmartMode(project).executeSynchronously();
                }
            });
        }
        return getModelRunner(className);
    }

    public void putModelRunner(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(join(className, project), modelRunner);
    }

    public void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        modelRunner.learnProject(project, progressIndicator);
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2("Resolving counter...");
        }
        modelRunner.resolveCounter();
        putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
        setLoaded(ProjectVariableNamesContributor.class, project, true);
        if (save) {
            if (progressIndicator != null) {
                progressIndicator.setText(IdNamesSuggestingBundle.message("saving.project.model", project.getName()));
            }
            double size = modelRunner.save(IdNamesSuggestingModelManager.getPath(project), progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    String.format("%s project model stats", project.getName()),
                    String.format("Size: %.3f Mb, Vocab size: %d",
                            size, vocabSize));
            System.out.printf("%s project model size is %.3f Mb\n", project.getName(), size);
            System.out.printf("Vocab size is %d\n", vocabSize);
        }
    }

    public void trainGlobalNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        NGramModelRunner modelRunner = IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor.class);
        if (modelRunner == null) {
            modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        }
        modelRunner.limitTrainingTime(false);
        modelRunner.learnProject(project, progressIndicator);
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2("Resolving counter...");
        }
        modelRunner.resolveCounter();
        putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
        setLoaded(GlobalVariableNamesContributor.class, true);
        if (save) {
            if (progressIndicator != null) {
                progressIndicator.setText(IdNamesSuggestingBundle.message("saving.global.model"));
            }
            double size = modelRunner.save(IdNamesSuggestingModelManager.getGlobalPath(), progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    "Global model stats",
                    String.format("Size: %.3f Mb, Vocab size: %d",
                            size, vocabSize));
            System.out.printf("Global model size is %.3f Mb\n", size);
            System.out.printf("Vocab size is %d\n", vocabSize);
        }
    }

    void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, boolean b) {
        if (b) {
            myLoadingTime.put(className.getName(), Instant.now());
        } else {
            myLoadingTime.remove(className.getName());
        }
    }

    void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, boolean b) {
        if (b) {
            myLoadingTime.put(join(className, project), Instant.now());
        } else {
            myLoadingTime.remove(join(className, project));
        }
    }

    public boolean isSomethingLoaded() {
        return !myLoadingTime.isEmpty();
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className) {
        return myLoadingTime.containsKey(className.getName());
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className, Project project) {
        return myLoadingTime.containsKey(join(className, project));
    }

    public Instant whenLoaded(@NotNull Class<? extends VariableNamesContributor> className) {
        return myLoadingTime.get(className.getName());
    }

    public Instant whenLoaded(@NotNull Class<? extends VariableNamesContributor> className, Project project) {
        return myLoadingTime.get(join(className, project));
    }

    private static @NotNull String join(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project) {
        return String.join("_", className.getName(), project.getLocationHash());
    }
}
