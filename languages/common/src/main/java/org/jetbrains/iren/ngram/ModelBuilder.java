package org.jetbrains.iren.ngram;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.history.core.Paths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.api.ModelRunner;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.services.ModelStatsService;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.storages.StringCounter;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

import static org.jetbrains.iren.ModelLoaderKt.downloadAndExtractIntellijModels;
import static org.jetbrains.iren.services.ModelManager.isIntellijProject;

public class ModelBuilder {
    private final Project myProject;
    private final LanguageSupporter mySupporter;
    private final ProgressIndicator myProgressIndicator;
    private final int vocabularyCutOff = AppSettingsState.getInstance().vocabularyCutOff;
    private final int maxTrainingTime = AppSettingsState.getInstance().maxTrainingTime;

    public static void trainModelsForAllProjectsInBackground() {
        ProgressManager.getInstance().run(new Task.Backgroundable(null, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                    trainModelsForAllLanguages(project, indicator, true);
                }
            }
        });
    }

    public static void trainInBackground(Project project) {
        if (loadIntellijModels(project)) return;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                trainModelsForAllLanguages(project, indicator, true);
            }
        });
    }

    public static void trainModelsForAllLanguages(@NotNull Project project,
                                                  @Nullable ProgressIndicator progressIndicator,
                                                  boolean save) {
        if (isIntellijProject(project)) return;
        if (progressIndicator != null) {
            progressIndicator.setText(IRenBundle.message("training.progress.indexing"));
//            Waits until indexes are prepared
            DumbService.getInstance(project).waitForSmartMode();
        }
        @NotNull ModelStatsService modelStats = ModelStatsService.getInstance();
        if (modelStats.isTraining()) return;
        modelStats.setTraining(true);
        try {
            for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
                trainWithSupporter(project, supporter, progressIndicator, save);
                if (progressIndicator != null && progressIndicator.isCanceled()) break;
            }
            modelStats.setTrainedTime(ProjectVariableNamesContributor.class, project);
        } finally {
            modelStats.setTraining(false);
            ConsistencyChecker.getInstance().dispose();
        }
    }

    public static void trainWithSupporter(Project project, LanguageSupporter supporter, ProgressIndicator progressIndicator, boolean save) {
        new ModelBuilder(project, supporter, progressIndicator).train(save);
    }

    public ModelBuilder(@NotNull Project project,
                        @NotNull LanguageSupporter supporter,
                        @Nullable ProgressIndicator progressIndicator) {
        myProject = project;
        mySupporter = supporter;
        myProgressIndicator = progressIndicator;
    }

    private void train(boolean save) {
        String name = ModelManager.getName(myProject, mySupporter.getLanguage());
        ModelStatsService.getInstance().setUsable(name, false);
        ModelManager.getInstance().removeModelRunner(name);
        if (myProgressIndicator != null)
            myProgressIndicator.setText(IRenBundle.message("training.progress.indicator.text",
                    myProject.getName(), mySupporter.getLanguage().getDisplayName()));
        System.out.printf("Project: %s\nLanguage: %s\n", myProject.getName(), mySupporter.getLanguage().getDisplayName());
        Instant start = Instant.now();
        NGramModelRunner modelRunner = new NGramModelRunner();
        if (!trainModelRunner(modelRunner)) return;
        String saveStats = null;
        if (save) {
            if (myProgressIndicator != null) myProgressIndicator.setText(IRenBundle.message("saving.project.model",
                    String.format("%s; %s", myProject.getName(), mySupporter.getLanguage())));

            final Path modelPath = ModelManager.getPath(name);
            modelRunner = new PersistentNGramModelRunner(modelRunner);
            double size = modelRunner.save(modelPath, myProgressIndicator);
            if (size <= 0 || !modelRunner.loadCounters(modelPath, myProgressIndicator)) return;
//            saveStats = IRenBundle.message("model.size", new DecimalFormat("#.##").format(size));
            saveStats = IRenBundle.message("model.size", size);
            System.out.println(saveStats);
        }
        NotificationsUtil.notificationAboutModel(
                myProject,
                IRenBundle.message("model.training.completed"),
                IRenBundle.message("model.training.statistics",
                        myProject.getName(),
                        mySupporter.getLanguage().getDisplayName(),
                        Duration.between(start, Instant.now()).toSeconds(),
                        modelRunner.getVocabulary().size())
                + "\n" + saveStats,
                saveStats != null ? ModelManager.getPath(name) : null
        );
        ModelManager.getInstance().putModelRunner(name, modelRunner);
        ModelStatsService.getInstance().setUsable(name, true);
    }

    public boolean trainModelRunner(@NotNull ModelRunner modelRunner) {
        modelRunner.train();
        if (myProgressIndicator != null) {
            myProgressIndicator.setIndeterminate(false);
        }
        final Collection<VirtualFile> files = getProjectFilesInSource();
        if (files.size() == 0) return false;
        ProgressBar progressBar = new ProgressBar(files.size(),
                myProgressIndicator,
                myProject.getBasePath() != null ? Paths.getParentOf(myProject.getBasePath()) : null);
        Instant trainingStart = Instant.now();
        if (vocabularyCutOff > 0) {
            trainVocabulary(modelRunner.getVocabulary(), files, progressBar, trainingStart);
            if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
                System.out.println("Training is canceled!");
                return false;
            }
        }
        trainNGramModel(modelRunner, files, progressBar, trainingStart);
        modelRunner.eval();
        return true;
    }

    private void trainVocabulary(Vocabulary vocabulary,
                                 @NotNull Collection<VirtualFile> files,
                                 ProgressBar progressBar,
                                 Instant trainingStart) {
        System.out.println("Training vocabulary...");
        StringCounter counter = new StringCounter();
        Collection<VirtualFile> viewedFiles = new ConcurrentLinkedQueue<>();
        files.parallelStream().filter(x -> trainingIsNotCanceled(myProgressIndicator,
                        trainingStart,
                        maxTrainingTime / 2))
                .forEach(file -> {
                    @Nullable PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(file));
                    progressBar.vocabularyTrainingStep(file);
                    if (psiFile == null) return;
                    counter.putAll(ReadAction.compute(() -> mySupporter.lexPsiFile(psiFile)));
                    viewedFiles.add(file);
                });
        files.clear();
        files.addAll(viewedFiles);
        VocabularyManager.clear(vocabulary);
        counter.toVocabulary(vocabulary, vocabularyCutOff);
        System.out.printf("Done in %s\n", Duration.between(trainingStart, Instant.now()));
    }

    private static boolean trainingIsNotCanceled(ProgressIndicator progressIndicator, Instant trainingStart, int maxTrainingTime) {
        return (progressIndicator == null || !progressIndicator.isCanceled()) &&
                (maxTrainingTime <= 0 || Duration.between(trainingStart, Instant.now())
                        .minusSeconds(maxTrainingTime).isNegative());
    }

    private void trainNGramModel(@NotNull ModelRunner modelRunner,
                                 @NotNull Collection<VirtualFile> files,
                                 @NotNull ProgressBar progressBar,
                                 Instant trainingStart) {
        System.out.println("Training NGram model...");
        progressBar.clear(files.size());
        Instant finalStart = Instant.now();
        files.parallelStream().filter(x -> trainingIsNotCanceled(myProgressIndicator, trainingStart, maxTrainingTime))
                .forEach(file -> {
                    try {
                        ReadAction.run(() -> ObjectUtils.consumeIfNotNull(PsiManager.getInstance(myProject).findFile(file), modelRunner::learnPsiFile));
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(file);
                    } finally {
                        progressBar.trainingStep(file);
                    }
                });
        System.out.printf("Done in %s\n", Duration.between(finalStart, Instant.now()));
        System.out.printf("Vocabulary size: %d\n", modelRunner.getVocabulary().size());
    }

    private Collection<VirtualFile> getProjectFilesInSource() {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
            return ReadAction.compute(() -> FileTypeIndex.getFiles(mySupporter.getFileType(),
                    GlobalSearchScope.projectScope(myProject)));
        }
        return ReadAction.compute(() -> FileTypeIndex.getFiles(mySupporter.getFileType(),
                        GlobalSearchScope.projectScope(myProject))
                .stream()
                .filter(ProjectFileIndex.getInstance(myProject)::isInSource)
                .collect(Collectors.toList()));
    }

    public static boolean loadIntellijModels(Project project) {
        if (!isIntellijProject(project)) return false;
        if (ModelManager.getInstance().containsIntellijModel()) return true;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("loading.project.model", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (ModelBuilder.loadModels(project, indicator)) return;
                downloadAndExtractIntellijModels(indicator);
                ModelBuilder.loadModels(project, indicator);
            }
        });
        return true;
    }

    public static boolean loadModels(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        boolean isSmthngLoaded = false;
        for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
            indicator.setText(IRenBundle.message("language", supporter.getLanguage().getDisplayName()));
            String name = ModelManager.getName(project, supporter.getLanguage());
            ModelRunner modelRunner = new PersistentNGramModelRunner();
            Instant start = Instant.now();
            final Path modelPath = ModelManager.getPath(name);
            boolean isLoaded = modelRunner.load(modelPath, indicator);
            isSmthngLoaded |= isLoaded;
            if (isLoaded) {
                modelRunner.getVocabulary().close();
                ModelManager.getInstance().putModelRunner(name, modelRunner);
                ModelStatsService.getInstance().setUsable(name, true);
                NotificationsUtil.notificationAboutModel(project,
                        IRenBundle.message("model.loaded"),
                        IRenBundle.message("model.loading.statistics",
                                project.getName(),
                                supporter.getLanguage().getDisplayName(),
                                Duration.between(start, Instant.now()).toSeconds(),
                                modelRunner.getVocabulary().size()),
                        modelPath);
            }
            if (indicator.isCanceled()) break;
        }
        return isSmthngLoaded;
    }
}

class ProgressBar {
    private final ProgressIndicator progressIndicator;
    private final String projectPath;
    int progress = 0;
    int total;
    boolean wasVocabTrained = false;

    public ProgressBar(int total, ProgressIndicator progressIndicator, @Nullable String projectPath) {
        this.total = total;
        this.progressIndicator = progressIndicator;
        this.projectPath = projectPath;
    }

    public void clear(int newTotal) {
        progress = 0;
        total = newTotal;
    }

    public synchronized void vocabularyTrainingStep(VirtualFile file) {
        wasVocabTrained = true;
        step(file, fraction -> fraction / 2);
    }

    public synchronized void trainingStep(VirtualFile file) {
        step(file, fraction -> wasVocabTrained ? 0.5 + fraction / 2 : fraction);
    }

    public synchronized void step(VirtualFile file, DoubleFunction<Double> modifyFraction) {
        double fraction = ++progress / (double) total;
        if (total < 10 || progress % (total / 10) == 0) {
            System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
        }
        if (progressIndicator != null) {
            progressIndicator.setText2(projectPath != null ? Paths.relativeIfUnder(file.getPath(), projectPath) : file.getPath());
            progressIndicator.setFraction(modifyFraction.apply(fraction));
        }
    }
}
