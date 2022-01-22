package org.jetbrains.iren.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.AbstractTrainModelAction;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.training.ModelBuilder;
import org.jetbrains.iren.services.ModelsUsabilityService;

public class TrainProjectNGramModelAction extends AbstractTrainModelAction {
    @Override
    protected void doActionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;
        ModelBuilder.trainInBackground(project);
    }

    @Override
    protected boolean canBePerformed(@NotNull AnActionEvent e) {
        return e.getProject() != null && !ModelsUsabilityService.getInstance().isTraining() &&
                LanguageSupporter.hasSupportedFiles(e.getProject());
    }
}
