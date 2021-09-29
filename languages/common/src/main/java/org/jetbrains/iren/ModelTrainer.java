package org.jetbrains.iren;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.contributors.GlobalVariableNamesContributor;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.impl.NGramModelRunner;
import org.jetbrains.iren.inspections.variable.PredictionsStorage;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.storages.StringCounter;
import org.jetbrains.iren.utils.LanguageSupporter;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ModelTrainer {
    public static void trainProjectNGramModelInBackground(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText(IRenBundle.message("training.progress.indicator.text", project.getName()));
                DumbService.getInstance(project).runWhenSmart(() ->
                        trainProjectNGramModel(project, indicator, true));
            }
        });
    }

    public static void trainProjectNGramModel(@NotNull Project project, @Nullable ProgressIndicator progressIndicator, boolean save) {
        if (ModelStatsService.getInstance().isTraining()) return;
        @NotNull ModelStatsService modelStats = ModelStatsService.getInstance();
        modelStats.setUsable(ProjectVariableNamesContributor.class, project, false);
        modelStats.setTraining(true);
        try {
            NGramModelRunner modelRunner = new NGramModelRunner(true);

            modelRunner.train();
            learnProject(modelRunner, project, progressIndicator);
            modelRunner.eval();

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
            modelRunner = new NGramModelRunner(true);
        }
        learnProject(modelRunner, project, progressIndicator);
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

    public static void learnProject(NGramModelRunner modelRunner, @NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(false);
        }
        final Collection<VirtualFile> files = new ArrayList<>();
        LanguageSupporter.INSTANCE.extensions().forEach(utils ->
                files.addAll(ReadAction.compute(() -> FileTypeIndex.getFiles(utils.getFileType(),
                        GlobalSearchScope.projectScope(project)))));
        final int[] progress = {0};
        Instant start = Instant.now();
        int vocabularyCutOff = AppSettingsState.getInstance().vocabularyCutOff;
        int maxTrainingTime = AppSettingsState.getInstance().maxTrainingTime;
        boolean vocabTraining = vocabularyCutOff > 0;
        if (vocabTraining) {
            System.out.printf("Training vocabulary on %s...\n", project.getName());
            StringCounter counter = new StringCounter();
            final int total = files.size();
            Collection<VirtualFile> viewedFiles = new ConcurrentLinkedQueue<>();
            files.parallelStream().filter(x -> (progressIndicator == null || !progressIndicator.isCanceled()) &&
                            (maxTrainingTime <= 0 || Duration.between(start, Instant.now())
                                    .minusSeconds(maxTrainingTime / 2).isNegative()))
                    .forEach(file -> {
                        @Nullable PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
                        if (psiFile == null) return;
                        counter.putAll(ReadAction.compute(() -> LanguageSupporter.getInstance(psiFile.getLanguage()).lexPsiFile(psiFile)));
                        viewedFiles.add(file);
                        synchronized (progress) {
                            double fraction = ++progress[0] / (double) total;
                            if (total < 10 || progress[0] % (total / 10) == 0) {
                                System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
                            }
                            if (progressIndicator != null) {
                                progressIndicator.setFraction(fraction / 2);
                            }
                        }
                    });
            files.clear();
            files.addAll(viewedFiles);
            VocabularyManager.clear(modelRunner.getVocabulary());
            counter.toVocabulary(modelRunner.getVocabulary(), vocabularyCutOff);
            System.out.printf("Done in %s\n", Duration.between(start, Instant.now()));
        }
        System.out.printf("Training NGram model on %s...\n", project.getName());
        progress[0] = 0;
        Instant finalStart = Instant.now();
        final int total = files.size();
        files.parallelStream().filter(x -> (progressIndicator == null || !progressIndicator.isCanceled()) &&
                        (maxTrainingTime <= 0 || Duration.between(start, Instant.now())
                                .minusSeconds(maxTrainingTime).isNegative()))
                .forEach(file -> {
                    ReadAction.run(() -> ObjectUtils.consumeIfNotNull(PsiManager.getInstance(project).findFile(file), modelRunner::learnPsiFile));
                    synchronized (progress) {
                        double fraction = ++progress[0] / (double) total;
                        if (total < 10 || progress[0] % (total / 10) == 0) {
                            System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
                        }
                        if (progressIndicator != null) {
                            progressIndicator.setText2(file.getPath());
                            progressIndicator.setFraction(vocabTraining ? 0.5 + fraction / 2 : fraction);
                        }
                    }
                });
        NotificationsUtil.notify(project,
                "NGram model training is completed.",
                String.format("Time of training on %s: %d ms.",
                        project.getName(),
                        Duration.between(start, Instant.now()).toMillis()));
        System.out.printf("Done in %s\n", Duration.between(finalStart, Instant.now()));
        System.out.printf("Vocabulary size: %d\n", modelRunner.getVocabulary().size());
    }
}
