package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;

public class LoadingGlobalModelStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.global.model")) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                ReadAction.nonBlocking(() -> (IdNamesSuggestingModelManager.getInstance()
                        .getModelRunner(GlobalVariableNamesContributor.class)).load(progressIndicator))
                        .inSmartMode(project)
                        .executeSynchronously();
            }
        });
    }
}
