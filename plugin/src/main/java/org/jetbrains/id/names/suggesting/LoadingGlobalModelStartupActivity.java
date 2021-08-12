package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.NGramVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;

public class LoadingGlobalModelStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.global.model")) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                ReadAction.nonBlocking(() -> {
                            NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
                            modelRunner.limitTrainingTime(false);
                            if (modelRunner.load(NGramModelRunner.GLOBAL_MODEL_DIRECTORY, progressIndicator)) {
                                modelRunner.getVocabulary().close();
                                modelRunner.getModel().getCounter().getCount(); // resolving counter
                                IdNamesSuggestingModelManager.getInstance()
                                        .putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
                                IdNamesSuggestingModelManager.getInstance()
                                        .setLoaded(GlobalVariableNamesContributor.class, true);
                            }
                        })
                        .inSmartMode(project)
                        .executeSynchronously();
            }
        });
    }
}
