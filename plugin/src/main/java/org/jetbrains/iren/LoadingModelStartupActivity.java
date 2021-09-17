package org.jetbrains.iren;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.contributors.NGramVariableNamesContributor;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.impl.NGramModelRunner;
import org.jetbrains.iren.rename.DefaultHandlersRemover;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.utils.NotificationsUtil;

import static org.jetbrains.iren.PluginLoadedListener.askPermissions;

public class LoadingModelStartupActivity implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
        DefaultHandlersRemover.remove();
        AppSettingsState settings = AppSettingsState.getInstance();
        if (!settings.firstOpen && settings.automaticTraining &&
                ModelStatsService.getInstance().needRetraining(ProjectVariableNamesContributor.class, project)) {
            ModelTrainer.trainProjectNGramModelInBackground(project);
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
                NotificationsUtil.notify(project, "Loading model", "");
                if (modelRunner.load(ModelManager.getPath(project), indicator)) {
                    modelRunner.getVocabulary().close();
                    modelRunner.resolveCounter();
                    ModelManager.getInstance().putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
                    ModelStatsService.getInstance().setUsable(ProjectVariableNamesContributor.class, project, true);
                    NotificationsUtil.notify(project, "Model is loaded", "");
                } else if (!settings.firstOpen && settings.automaticTraining) {
                    indicator.setText(IRenBundle.message("training.progress.indicator.text", project.getName()));
                    ModelTrainer.trainProjectNGramModel(project, indicator, true);
                }
            }
        });
        askPermissions();
    }
}
