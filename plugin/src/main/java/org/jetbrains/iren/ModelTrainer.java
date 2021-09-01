package org.jetbrains.iren;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.contributors.GlobalVariableNamesContributor;
import org.jetbrains.iren.contributors.NGramVariableNamesContributor;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.impl.NGramModelRunner;
import org.jetbrains.iren.inspections.variable.PredictionsStorage;
import org.jetbrains.iren.utils.NotificationsUtil;

public class ModelTrainer {
    public static void trainProjectNGramModelInBackground(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText(IRenBundle.message("training.progress.indicator.text", project.getName()));
                ModelTrainer.trainProjectNGramModel(project, indicator, true);
            }
        });
    }

    public static void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        if (ModelStatsService.getInstance().isTraining()) return;
        @NotNull ModelStatsService modelStats = ModelStatsService.getInstance();
        modelStats.setUsable(ProjectVariableNamesContributor.class, project, false);
        modelStats.setTraining(true);
        try {
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
                    progressIndicator.setText(IRenBundle.message("saving.project.model", project.getName()));
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
            modelStats.setTrainedTime(ProjectVariableNamesContributor.class, project);
            modelStats.setUsable(ProjectVariableNamesContributor.class, project, true);
        } finally {
            modelStats.setTraining(false);
            PredictionsStorage.Companion.getInstance().dispose();
        }
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
                progressIndicator.setText(IRenBundle.message("saving.global.model"));
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
        ModelStatsService.getInstance().setTrainedTime(GlobalVariableNamesContributor.class);
    }
}
