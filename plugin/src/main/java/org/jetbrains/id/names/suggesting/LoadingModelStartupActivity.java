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
import org.jetbrains.id.names.suggesting.utils.NotificationsUtil;

import static org.jetbrains.id.names.suggesting.PluginLoadedListener.askPermissions;

public class LoadingModelStartupActivity implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
        askPermissions();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (ModelStatsService.getInstance().needRetraining(ProjectVariableNamesContributor.class, project)) {
                    indicator.setText(IdNamesSuggestingBundle.message("training.progress.indicator.text", project.getName()));
                    ReadAction.nonBlocking(() -> ModelTrainer.trainProjectNGramModel(project, indicator, true))
                            .inSmartMode(project).executeSynchronously();
                    return;
                }
                NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
                NotificationsUtil.notify(project, "Loading model", "");
                if (modelRunner.load(ModelManager.getPath(project), indicator)) {
                    modelRunner.getVocabulary().close();
                    modelRunner.resolveCounter();
                    ModelManager.getInstance().putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
                    ModelStatsService.getInstance().setLoaded(ProjectVariableNamesContributor.class, project, true);
                    NotificationsUtil.notify(project, "Model is loaded", "");
                }
            }
        });
    }
}
