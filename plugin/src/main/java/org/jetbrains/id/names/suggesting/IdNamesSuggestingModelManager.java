package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;
import org.jetbrains.id.names.suggesting.utils.NotificationsUtil;

import java.util.HashMap;
import java.util.Map;

public class IdNamesSuggestingModelManager {
    private final Map<String, IdNamesSuggestingModelRunner> myModelRunners = new HashMap<>();

    public IdNamesSuggestingModelManager() {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
    }

    public static IdNamesSuggestingModelManager getInstance() {
        return ServiceManager.getService(IdNamesSuggestingModelManager.class);
    }

    public IdNamesSuggestingModelRunner getModelRunner(Class<? extends VariableNamesContributor> name) {
        return myModelRunners.get(name.getName());
    }

    public void putModelRunner(Class<? extends VariableNamesContributor> name, IdNamesSuggestingModelRunner modelRunner) {
        myModelRunners.put(name.getName(), modelRunner);
    }

    public IdNamesSuggestingModelRunner getModelRunner(Class<? extends VariableNamesContributor> className, Project project) {
        IdNamesSuggestingModelRunner modelRunner = myModelRunners.get(String.join("_", className.getName(), project.getLocationHash()));
        if (modelRunner != null){
            return modelRunner;
        }
        return getModelRunner(className);
    }

    public void putModelRunner(Class<? extends VariableNamesContributor> className, Project project, IdNamesSuggestingModelRunner modelRunner) {
        myModelRunners.put(String.join("_", className.getName(), project.getLocationHash()), modelRunner);
    }

    public void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        modelRunner.learnProject(project, progressIndicator);
        putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
    }

    public void trainGlobalNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        IdNamesNGramModelRunner modelRunner = (IdNamesNGramModelRunner) IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor.class);
        modelRunner.learnProject(project, progressIndicator);
        if (save) {
            double size = modelRunner.save(progressIndicator);
            NotificationsUtil.notify(project,
                    "Global model size",
                    String.format("is %.3f Mb",
                            size));
            System.out.printf("Global model size is %.3f Mb.\n", size);
        }
    }
}
