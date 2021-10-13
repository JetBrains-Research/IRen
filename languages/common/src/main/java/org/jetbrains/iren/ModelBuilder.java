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
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.impl.NGramModelRunner;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.services.ModelStatsService;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.storages.StringCounter;
import org.jetbrains.iren.utils.LanguageSupporter;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ModelBuilder {
    public static void trainProjectNGramModelInBackground(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                trainProjectNGramModel(project, indicator, true);
            }
        });
    }

    public static void trainProjectNGramModel(@NotNull Project project,
                                              @Nullable ProgressIndicator progressIndicator,
                                              boolean save) {
        if (progressIndicator != null) {
            progressIndicator.setText(IRenBundle.message("training.progress.indexing"));
//            Waits until indexes are prepared
            DumbService.getInstance(project).waitForSmartMode();
            progressIndicator.setText(IRenBundle.message("training.progress.indicator.text", project.getName()));
        }
        @NotNull ModelStatsService modelStats = ModelStatsService.getInstance();
        if (modelStats.isTraining()) return;
        modelStats.setTraining(true);
        try {
            for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
                trainProjectNGramModelWithSupporter(project, supporter, progressIndicator, save);
                if (progressIndicator != null && progressIndicator.isCanceled()) break;
            }
            modelStats.setTrainedTime(ProjectVariableNamesContributor.class, project);
        } finally {
            modelStats.setTraining(false);
            ConsistencyChecker.getInstance().dispose();
        }
    }

    public static void trainProjectNGramModelWithSupporter(@NotNull Project project,
                                                           @NotNull LanguageSupporter supporter,
                                                           @Nullable ProgressIndicator progressIndicator,
                                                           boolean save) {
        String name = ModelManager.getName(project, supporter.getLanguage());
        NGramModelRunner modelRunner = new NGramModelRunner();
        ModelStatsService.getInstance().setUsable(name, false);
        if (progressIndicator != null)
            progressIndicator.setText(IRenBundle.message("training.progress.indicator.text",
                    String.format("%s; %s", project.getName(), supporter.getLanguage())));

        modelRunner.train();
        if (!learnProject(modelRunner, project, supporter, progressIndicator)) return;
        modelRunner.eval();

        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return;
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2("Resolving counter...");
        }
        modelRunner.resolveCounter();
        ModelManager.getInstance().putModelRunner(name, modelRunner);
        if (save) {
            if (progressIndicator != null) progressIndicator.setText(IRenBundle.message("saving.project.model",
                    String.format("%s; %s", project.getName(), supporter.getLanguage())));

            double size = modelRunner.save(ModelManager.getPath(name), progressIndicator);
            int vocabSize = modelRunner.getVocabulary().size();
            NotificationsUtil.notify(project,
                    String.format("Project: %s; %s", project.getName(), supporter.getLanguage()),
                    String.format("Model size: %.3f Mb; Vocab size: %d",
                            size, vocabSize));
            System.out.printf("Project: %s;\t%s;\tModel size: %.3f Mb;\tVocab size: %d\n", project.getName(), supporter.getLanguage(), size, vocabSize);
        }
        ModelStatsService.getInstance().setUsable(name, true);
    }

    public static boolean learnProject(NGramModelRunner modelRunner,
                                       @NotNull Project project,
                                       LanguageSupporter supporter,
                                       @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(false);
        }
        final Collection<VirtualFile> files = ReadAction.compute(() -> FileTypeIndex.getFiles(supporter.getFileType(),
                GlobalSearchScope.projectScope(project)));
        if (files.size() == 0) return false;
        final int[] progress = {0};
        Instant start = Instant.now();
        int vocabularyCutOff = AppSettingsState.getInstance().vocabularyCutOff;
        int maxTrainingTime = AppSettingsState.getInstance().maxTrainingTime;
        boolean vocabTraining = vocabularyCutOff > 0;
        if (vocabTraining) {
            System.out.println("Training vocabulary...");
            StringCounter counter = new StringCounter();
            final int total = files.size();
            Collection<VirtualFile> viewedFiles = new ConcurrentLinkedQueue<>();
            files.parallelStream().filter(x -> (progressIndicator == null || !progressIndicator.isCanceled()) &&
                            (maxTrainingTime <= 0 || Duration.between(start, Instant.now())
                                    .minusSeconds(maxTrainingTime / 2).isNegative()))
                    .forEach(file -> {
                        @Nullable PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
                        if (psiFile == null) return;
                        counter.putAll(ReadAction.compute(() -> supporter.lexPsiFile(psiFile)));
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
            if (progressIndicator != null && progressIndicator.isCanceled()) {
                System.out.println("Training is canceled!");
                return false;
            }
        }
        System.out.println("Training NGram model...");
        progress[0] = 0;
        Instant finalStart = Instant.now();
        final int total = files.size();
        files.parallelStream().filter(x -> (progressIndicator == null || !progressIndicator.isCanceled()) &&
                        (maxTrainingTime <= 0 || Duration.between(start, Instant.now())
                                .minusSeconds(maxTrainingTime).isNegative()))
                .forEach(file -> {
                    try {
                        ReadAction.run(() -> ObjectUtils.consumeIfNotNull(PsiManager.getInstance(project).findFile(file), modelRunner::learnPsiFile));
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(file);
                    }
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
                String.format("%s; Time of training: %d ms.",
                        supporter.getLanguage(),
                        Duration.between(start, Instant.now()).toMillis()));
        System.out.printf("Done in %s\n", Duration.between(finalStart, Instant.now()));
        System.out.printf("Vocabulary size: %d\n", modelRunner.getVocabulary().size());
        return true;
    }

    public static boolean loadModels(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        NotificationsUtil.notify(project, "Loading models...", "");
        boolean isSmthngLoaded = false;
        for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
            indicator.setText(supporter.getLanguage().toString());
            String name = ModelManager.getName(project, supporter.getLanguage());
            NGramModelRunner modelRunner = new NGramModelRunner();
            boolean isLoaded = modelRunner.load(ModelManager.getPath(name), indicator);
            isSmthngLoaded |= isLoaded;
            if (isLoaded) {
                NotificationsUtil.notify(project, supporter.getLanguage().toString(), "");
                modelRunner.getVocabulary().close();
                modelRunner.resolveCounter();
                ModelManager.getInstance().putModelRunner(name, modelRunner);
                ModelStatsService.getInstance().setUsable(name, true);
            }
            if (indicator.isCanceled()) break;
        }
        NotificationsUtil.notify(project, isSmthngLoaded ? "Models are loaded!" : "There are no saved models!", "");
        return isSmthngLoaded;
    }
}
