package org.jetbrains.iren.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.IdNamesSuggestingBundle;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.ModelTrainer;
import org.jetbrains.iren.api.AbstractTrainModelAction;

public class TrainGlobalNGramModelAction extends AbstractTrainModelAction {
    @Override
    protected void doActionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("global.training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                progressIndicator.setText(IdNamesSuggestingBundle.message("global.training.progress.indicator.text", project.getName()));
                ModelTrainer.trainGlobalNGramModel(project, progressIndicator, true);
            }
        });
    }

    @Override
    protected boolean canBePerformed(@NotNull AnActionEvent e) {
        return e.getProject() != null && !ModelStatsService.getInstance().isTraining() &&
                FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(e.getProject()));
    }
}
