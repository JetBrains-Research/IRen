package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;

public class LoadingModelStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (ModelSaveTimeService.getInstance().needRetraining(ProjectVariableNamesContributor.class, project)) {
                    indicator.setText(IdNamesSuggestingBundle.message("training.progress.indicator.text", project.getName()));
                    ReadAction.nonBlocking(() -> ModelTrainer.trainProjectNGramModel(project, indicator, true))
                            .inSmartMode(project).executeSynchronously();
                    return;
                }
                NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
                if (modelRunner.load(ModelManager.getPath(project), indicator)) {
                    modelRunner.getVocabulary().close();
                    modelRunner.resolveCounter();
                    ModelManager.getInstance().putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
                    ModelSaveTimeService.getInstance().setTrained(ProjectVariableNamesContributor.class, project, true);
                }
            }
        });
    }
}
