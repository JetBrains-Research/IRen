package org.jetbrains.iren.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.ModelTrainer;
import org.jetbrains.iren.api.AbstractTrainModelAction;

public class TrainProjectNGramModelAction extends AbstractTrainModelAction {
    @Override
    protected void doActionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText(IRenBundle.message("training.progress.indicator.text", project.getName()));
                ReadAction.nonBlocking(() -> ModelTrainer.trainProjectNGramModel(project, indicator, true))
                        .inSmartMode(project).executeSynchronously();
            }
        });
    }

    @Override
    protected boolean canBePerformed(@NotNull AnActionEvent e) {
        return e.getProject() != null && !ModelStatsService.getInstance().isTraining() &&
                FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(e.getProject()));
    }
}
