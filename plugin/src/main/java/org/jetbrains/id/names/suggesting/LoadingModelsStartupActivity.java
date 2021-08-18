package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;

public class LoadingModelsStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        @NotNull ProgressManager progressManager = ProgressManager.getInstance();
        progressManager.run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
                @NotNull IdNamesSuggestingModelManager modelManager = IdNamesSuggestingModelManager.getInstance();
                if (modelRunner.load(IdNamesSuggestingModelManager.getPath(project), indicator)) {
                    modelRunner.getVocabulary().close();
                    modelRunner.resolveCounter();
                    modelManager.putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
                    modelManager.setLoaded(ProjectVariableNamesContributor.class, project, true);
                } else { // TODO: ask developer if he wants to automate training on a new project
                    indicator.setText(IdNamesSuggestingBundle.message("training.progress.indicator.text", project.getName()));
                    ReadAction.nonBlocking(() -> modelManager.trainProjectNGramModel(project, indicator, true))
                            .inSmartMode(project).executeSynchronously();
                }
            }
        });
        progressManager.run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.global.model")) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
                modelRunner.limitTrainingTime(false);
                if (modelRunner.load(IdNamesSuggestingModelManager.getGlobalPath(), progressIndicator)) {
                    modelRunner.getVocabulary().close();
                    modelRunner.resolveCounter();
                    IdNamesSuggestingModelManager.getInstance()
                            .putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
                    IdNamesSuggestingModelManager.getInstance()
                            .setLoaded(GlobalVariableNamesContributor.class, true);
                }
            }
        });
    }
}
