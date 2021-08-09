package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;
import org.jetbrains.id.names.suggesting.utils.NotificationsUtil;

import java.util.HashMap;
import java.util.Map;

public class IdNamesSuggestingModelManager {
    private final Map<String, NGramModelRunner> myModelRunners = new HashMap<>();

    public IdNamesSuggestingModelManager() {
        NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
    }

    public static IdNamesSuggestingModelManager getInstance() {
        return ServiceManager.getService(IdNamesSuggestingModelManager.class);
    }

    public NGramModelRunner getModelRunner(Class<? extends VariableNamesContributor> name) {
        return myModelRunners.get(name.getName());
    }

    public void putModelRunner(Class<? extends VariableNamesContributor> name, NGramModelRunner modelRunner) {
        myModelRunners.put(name.getName(), modelRunner);
    }

    public NGramModelRunner getModelRunner(Class<? extends VariableNamesContributor> className, Project project) {
        NGramModelRunner modelRunner = myModelRunners.get(String.join("_", className.getName(), project.getLocationHash()));
        if (modelRunner != null) {
            return modelRunner;
        }
        return getModelRunner(className);
    }

    public void putModelRunner(Class<? extends VariableNamesContributor> className, Project project, NGramModelRunner modelRunner) {
        myModelRunners.put(String.join("_", className.getName(), project.getLocationHash()), modelRunner);
    }

    public void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        modelRunner.learnProject(project, progressIndicator);
        putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
    }

    public void trainGlobalNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        NGramModelRunner modelRunner = IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor.class);
        modelRunner.learnProject(project, progressIndicator);
        if (save) {
            double size = modelRunner.save(progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    "Global model stats",
                    String.format("Size: %.3f Mb, Vocab size: %d",
                            size, vocabSize));
            System.out.printf("Global model size is %.3f Mb\n", size);
            System.out.printf("Vocab size is %d\n", vocabSize);
        }
    }
}
