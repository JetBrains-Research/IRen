package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IdNamesSuggestingModelManager {
    private final Set<String> isLoaded = new HashSet<>();
    private final Map<String, NGramModelRunner> myModelRunners = new HashMap<>();

    public IdNamesSuggestingModelManager(){
        setLoaded(FileVariableNamesContributor.class, true);
    }

    public static @NotNull IdNamesSuggestingModelManager getInstance() {
        return ServiceManager.getService(IdNamesSuggestingModelManager.class);
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull Class<? extends VariableNamesContributor> name) {
        return myModelRunners.get(name.getName());
    }

    public void putModelRunner(@NotNull Class<? extends VariableNamesContributor> name, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(name.getName(), modelRunner);
    }

    public @Nullable NGramModelRunner getModelRunner(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project) {
        NGramModelRunner modelRunner = myModelRunners.get(String.join("_", className.getName(), project.getLocationHash()));
        if (modelRunner != null) {
            return modelRunner;
        }
        return getModelRunner(className);
    }

    public void putModelRunner(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, @NotNull NGramModelRunner modelRunner) {
        myModelRunners.put(String.join("_", className.getName(), project.getLocationHash()), modelRunner);
    }

    public void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        modelRunner.learnProject(project, progressIndicator);
        modelRunner.getModel().getCounter().getCount(); // resolving counter
        putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
        setLoaded(ProjectVariableNamesContributor.class, project, true);
    }

    public void trainGlobalNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        NGramModelRunner modelRunner = IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor.class);
        if (modelRunner == null) {
            modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
            putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
        }
        modelRunner.setVocabularyCutOff(0);
        modelRunner.limitTrainingTime(false);
        modelRunner.learnProject(project, progressIndicator);
        modelRunner.getModel().getCounter().getCount(); // resolving counter
        setLoaded(GlobalVariableNamesContributor.class, true);
        if (save) {
            double size = modelRunner.save(NGramModelRunner.GLOBAL_MODEL_DIRECTORY, progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    "Global model stats",
                    String.format("Size: %.3f Mb, Vocab size: %d",
                            size, vocabSize));
            System.out.printf("Global model size is %.3f Mb\n", size);
            System.out.printf("Vocab size is %d\n", vocabSize);
        }
    }
    
    void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, boolean b){
        if (b) {
            isLoaded.add(className.getName());
        } else {
            isLoaded.remove(className.getName());
        }
    }

    void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, boolean b){
        if (b) {
            isLoaded.add(String.join("_", className.getName(), project.getLocationHash()));
        } else {
            isLoaded.remove(String.join("_", className.getName(), project.getLocationHash()));
        }
    }

    public boolean isSomethingLoaded() {
        return !isLoaded.isEmpty();
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className) {
        return isLoaded.contains(className.getName());
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className, Project project) {
        return isLoaded.contains(String.join("_", className.getName(), project.getLocationHash()));
    }
}
