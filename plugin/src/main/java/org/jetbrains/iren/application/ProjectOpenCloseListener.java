package org.jetbrains.iren.application;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.training.ModelBuilder;
import org.jetbrains.iren.services.NGramModelManager;

public class ProjectOpenCloseListener implements ProjectManagerListener {
    @Override
    public void projectOpened(@NotNull Project project) {
        LanguageSupporter.removeRenameHandlers();
        ModelBuilder.prepareIRenModels(project);
    }

    @Override
    public void projectClosed(@NotNull Project project) {
        NGramModelManager.getInstance(project).removeProjectModelRunners(project);
    }
}
