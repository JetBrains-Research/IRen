package tools.varMiner;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.IdNamesSuggestingBundle;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.time.Duration;
import java.time.Instant;

public class DatasetBuildAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null &&
                FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("building.dataset.title")) {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    progressIndicator.setText(IdNamesSuggestingBundle.message("building.dataset.for.project", project.getName()));
                    ReadAction.nonBlocking(() -> {
                        Instant start = Instant.now();
                        DatasetExtractor.build(project, progressIndicator);
                        Instant end = Instant.now();
                        NotificationsUtil.notify(project,
                                "Building dataset is completed.",
                                String.format("Time of building on %s: %dms.",
                                        project.getName(),
                                        Duration.between(start, end).toMillis()));
                    })
                            .inSmartMode(project)
                            .executeSynchronously();
                }
            });
        }
    }
}
