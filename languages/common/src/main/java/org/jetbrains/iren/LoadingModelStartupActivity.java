package org.jetbrains.iren;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.services.ModelStatsService;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.utils.LanguageSupporter;

import static org.jetbrains.iren.PluginLoadedListener.askPermissions;

public class LoadingModelStartupActivity implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
        LanguageSupporter.removeRenameHandlers();
        AppSettingsState settings = AppSettingsState.getInstance();
        if (!settings.firstOpen && settings.automaticTraining &&
                ModelStatsService.getInstance().needRetraining(ProjectVariableNamesContributor.class, project)) {
            ModelBuilder.trainProjectNGramModelInBackground(project);
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (!ModelBuilder.loadModels(project, indicator) && !settings.firstOpen && settings.automaticTraining) {
                    ModelBuilder.trainProjectNGramModel(project, indicator, true);
                }
            }
        });
        askPermissions();
    }
}
