package org.jetbrains.iren;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.contributors.GlobalVariableNamesContributor;
import org.jetbrains.iren.contributors.NGramVariableNamesContributor;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.impl.NGramModelRunner;
import org.jetbrains.iren.utils.NotificationsUtil;

public class ModelTrainer {
    public static void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        @NotNull ModelStatsService loadingService = ModelStatsService.getInstance();
        loadingService.setTrained(ProjectVariableNamesContributor.class, project, false);
        loadingService.setLoaded(ProjectVariableNamesContributor.class, project, false);
        loadingService.setTraining(true);
        NGramModelRunner modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);

        modelRunner.learnProject(project, progressIndicator);
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2("Resolving counter...");
        }
        modelRunner.resolveCounter();
        ModelManager.getInstance().putModelRunner(ProjectVariableNamesContributor.class, project, modelRunner);
        if (save) {
            if (progressIndicator != null) {
                progressIndicator.setText(IdNamesSuggestingBundle.message("saving.project.model", project.getName()));
            }
            double size = modelRunner.save(ModelManager.getPath(project), progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    String.format("%s project model stats", project.getName()),
                    String.format("Size: %.3f Mb, Vocab size: %d",
                            size, vocabSize));
            System.out.printf("%s project model size is %.3f Mb\n", project.getName(), size);
            System.out.printf("Vocab size is %d\n", vocabSize);
        }
        loadingService.setTrained(ProjectVariableNamesContributor.class, project, true);
        loadingService.setLoaded(ProjectVariableNamesContributor.class, project, true);
        loadingService.setTraining(false);
    }

    public static void trainGlobalNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        NGramModelRunner modelRunner = ModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor.class);
        if (modelRunner == null) {
            modelRunner = new NGramModelRunner(NGramVariableNamesContributor.SUPPORTED_TYPES, true);
        }
        modelRunner.learnProject(project, progressIndicator);
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2("Resolving counter...");
        }
        modelRunner.resolveCounter();
        ModelManager.getInstance().putModelRunner(GlobalVariableNamesContributor.class, modelRunner);
        if (save) {
            if (progressIndicator != null) {
                progressIndicator.setText(IdNamesSuggestingBundle.message("saving.global.model"));
            }
            double size = modelRunner.save(ModelManager.getGlobalPath(), progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    "Global model stats",
                    String.format("Size: %.3f Mb, Vocab size: %d",
                            size, vocabSize));
            System.out.printf("Global model size is %.3f Mb\n", size);
            System.out.printf("Vocab size is %d\n", vocabSize);
        }
        ModelStatsService.getInstance().setTrained(GlobalVariableNamesContributor.class, true);
    }
}
