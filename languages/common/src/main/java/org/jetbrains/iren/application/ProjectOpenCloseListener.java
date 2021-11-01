package org.jetbrains.iren.application;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.ngram.ModelBuilder;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.services.ModelStatsService;
import org.jetbrains.iren.settings.AppSettingsState;

public class ProjectOpenCloseListener implements ProjectManagerListener {
    @Override
    public void projectOpened(@NotNull Project project) {
        LanguageSupporter.removeRenameHandlers();
        AppSettingsState settings = AppSettingsState.getInstance();
        if (!settings.firstOpen && settings.automaticTraining &&
                ModelStatsService.getInstance().needRetraining(ProjectVariableNamesContributor.class, project)) {
            ModelBuilder.trainInBackground(project);
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (!ModelBuilder.loadModels(project, indicator) && !settings.firstOpen && settings.automaticTraining) {
                    ModelBuilder.trainModelsForAllLanguages(project, indicator, true);
                }
            }
        });
    }

    @Override
    public void projectClosed(@NotNull Project project) {
        ModelManager.getInstance().removeProjectModelRunners(project);
    }
}
