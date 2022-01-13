package org.jetbrains.iren.training;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.history.core.Paths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import org.jetbrains.iren.ngram.NGramModelRunner;
import org.jetbrains.iren.ngram.PersistentNGramModelRunner;
import org.jetbrains.iren.ngram.VocabularyManager;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.services.ModelsSaveTime;
import org.jetbrains.iren.services.ModelsUsabilityService;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.storages.StringCounter;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.jetbrains.iren.ModelLoaderKt.downloadAndExtractIntellijModels;
import static org.jetbrains.iren.training.ModelBuilder.TrainingStatus.*;
import static org.jetbrains.iren.utils.IdeaUtil.isIdeaProject;

public class ModelBuilder {
    private static final Logger LOG = Logger.getInstance(ModelBuilder.class);
    private final Project myProject;
    private final LanguageSupporter mySupporter;
    private final ProgressIndicator myProgressIndicator;
    private final int vocabularyCutOff = AppSettingsState.getInstance().vocabularyCutOff;
    private final int maxTrainingTime = AppSettingsState.getInstance().maxTrainingTime;
    private final MemoryListener memoryListener = new MemoryListener();

    public ModelBuilder(@NotNull Project project,
                        @NotNull LanguageSupporter supporter,
                        @Nullable ProgressIndicator progressIndicator) {
        myProject = project;
        mySupporter = supporter;
        myProgressIndicator = progressIndicator;
    }

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

    private static void trainModelsForAllLanguages(@NotNull Project project,
                                                   @NotNull ProgressIndicator progressIndicator,
                                                   boolean save) {
        if (isIdeaProject(project)) {
            loadModelsOrIntellij(project, progressIndicator);
            return;
        }
        progressIndicator.setText(IRenBundle.message("training.progress.indexing"));
//            Waits until indexes are prepared
        DumbService.getInstance(project).waitForSmartMode();
        @NotNull ModelsUsabilityService usabilityService = ModelsUsabilityService.getInstance();
        if (usabilityService.isTraining()) return;
        usabilityService.setTraining(true);
        try {
            for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
                trainWithSupporter(project, supporter, progressIndicator, save);
                if (progressIndicator.isCanceled()) break;
            }
            ModelsSaveTime.getInstance().setTrainedTime(project);
        } finally {
            usabilityService.setTraining(false);
            ConsistencyChecker.getInstance().dispose();
        }
    }

    private static void trainWithSupporter(Project project, LanguageSupporter supporter, ProgressIndicator progressIndicator, boolean save) {
        new ModelBuilder(project, supporter, progressIndicator).train(save);
    }

    private void train(boolean save) {
        String name = ModelManager.getName(myProject, mySupporter.getLanguage());
        ModelsUsabilityService.getInstance().setUsable(name, false);
        ModelManager.getInstance().removeModelRunner(name);
        if (myProgressIndicator != null)
            myProgressIndicator.setText(IRenBundle.message("training.progress.indicator.text",
                    myProject.getName(), mySupporter.getLanguage().getDisplayName()));
        System.out.printf("Project: %s\nLanguage: %s\n", myProject.getName(), mySupporter.getLanguage().getDisplayName());
        Instant start = Instant.now();
        NGramModelRunner modelRunner = new NGramModelRunner();
        TrainingStatus status = trainModelRunner(modelRunner);
        if (status == CANCELED_OR_FAILED) return;
        double modelSize = 0;
        if (save) {
            if (myProgressIndicator != null)
                myProgressIndicator.setText(IRenBundle.message("saving.text", myProject.getName(), mySupporter.getLanguage().getDisplayName()));
            final Path modelPath = ModelManager.getPath(name);
            modelRunner = new PersistentNGramModelRunner(modelRunner);
            modelSize = modelRunner.save(modelPath, myProgressIndicator);
            if (modelSize <= 0 || !modelRunner.loadCounters(modelPath, myProgressIndicator)) return;
            System.out.println(IRenBundle.message("model.size", modelSize));
        }
        NotificationsUtil.modelTrained(myProject,
                mySupporter,
                status == FULLY_COMPLETED,
                start,
                modelRunner.getVocabulary().size(),
                modelSize);
        ModelManager.getInstance().putModelRunner(name, modelRunner);
        ModelsUsabilityService.getInstance().setUsable(name, true);
    }

    public TrainingStatus trainModelRunner(@NotNull ModelRunner modelRunner) {
        modelRunner.train();
        if (myProgressIndicator != null) {
            myProgressIndicator.setIndeterminate(false);
        }
        final Collection<VirtualFile> files = getProjectFilesInSource();
        if (files.size() == 0) return CANCELED_OR_FAILED;
        ProgressBar progressBar = new ProgressBar(files.size(),
                myProgressIndicator,
                myProject.getBasePath() != null ? Paths.getParentOf(myProject.getBasePath()) : null);
        Instant trainingStart = Instant.now();
        if (vocabularyCutOff > 0) {
            trainVocabulary(modelRunner.getVocabulary(), files, progressBar, trainingStart);
            if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
                System.out.println("Training is canceled!");
                return CANCELED_OR_FAILED;
            }
        }
        TrainingStatus status = trainNGramModel(modelRunner, files, progressBar, trainingStart);
        modelRunner.eval();
        return status;
    }

    private void trainVocabulary(Vocabulary vocabulary,
                                 @NotNull Collection<VirtualFile> files,
                                 ProgressBar progressBar,
                                 Instant trainingStart) {
        System.out.println("Training vocabulary...");
        StringCounter counter = new StringCounter();
        Collection<VirtualFile> viewedFiles = new ConcurrentLinkedQueue<>();
        files.parallelStream().filter(x -> trainingIsNotCanceled(trainingStart,
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

    private boolean trainingIsNotCanceled(Instant trainingStart, int maxTrainingTime) {
        return !myProject.isDisposed() && !memoryListener.shouldCancel() && (myProgressIndicator == null || !myProgressIndicator.isCanceled()) &&
                (maxTrainingTime <= 0 || Duration.between(trainingStart, Instant.now())
                        .minusSeconds(maxTrainingTime).isNegative());
    }

    private @NotNull TrainingStatus trainNGramModel(@NotNull ModelRunner modelRunner,
                                                    @NotNull Collection<VirtualFile> files,
                                                    @NotNull ProgressBar progressBar,
                                                    Instant trainingStart) {
        System.out.println("Training NGram model...");
        progressBar.clear(files.size());
        Instant start = Instant.now();
        files.parallelStream().filter(x -> trainingIsNotCanceled(trainingStart, maxTrainingTime))
                .forEach(file -> {
                    try {
                        ReadAction.run(() -> ObjectUtils.consumeIfNotNull(PsiManager.getInstance(myProject).findFile(file), modelRunner::learnPsiFile));
                    } catch (ProcessCanceledException ignore) {
                    } catch (Exception e) {
                        System.out.printf("Failed to train on: %s\n", file);
                        e.printStackTrace();
                    } finally {
                        progressBar.trainingStep(file);
                    }
                });
        System.out.printf("Done in %s\n", Duration.between(start, Instant.now()));
        System.out.printf("Vocabulary size: %d\n", modelRunner.getVocabulary().size());
        return trainingIsNotCanceled(trainingStart, maxTrainingTime) ?
                TrainingStatus.FULLY_COMPLETED :
                NON_FULLY_COMPLETED;
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

    private static boolean loadModelsOrIntellij(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        if (isIdeaProject(project)) {
            LOG.info(String.format("Project %s was defined as intellij", project.getName()));
            if (ModelManager.getInstance().containsIntellijModel() || loadModels(project, indicator))
                return true;
            final AppSettingsState settings = AppSettingsState.getInstance();
            if (!settings.firstOpen && settings.automaticTraining) downloadAndExtractIntellijModels(indicator);
        }
        return loadModels(project, indicator);
    }

    private static boolean loadModels(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        boolean isSmthngLoaded = false;
        for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
            indicator.setText(IRenBundle.message("loading.text", project.getName(), supporter.getLanguage().getDisplayName()));
            String name = ModelManager.getName(project, supporter.getLanguage());
            ModelRunner modelRunner = new PersistentNGramModelRunner();
            Instant start = Instant.now();
            final Path modelPath = ModelManager.getPath(name);
            boolean isLoaded = modelRunner.load(modelPath, indicator);
            isSmthngLoaded |= isLoaded;
            if (isLoaded) {
                modelRunner.getVocabulary().close();
                ModelManager.getInstance().putModelRunner(name, modelRunner);
                ModelsUsabilityService.getInstance().setUsable(name, true);
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

    public static void trainInBackground(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                trainModelsForAllLanguages(project, indicator, true);
            }
        });
    }

    public static void prepareIRenModels(@NotNull Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IRenBundle.message("prepare.models.title", project.getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText(IRenBundle.message("delete.old.models.process"));
                if (ModelManager.deleteOldModels()) NotificationsUtil.oldModelsDeleted();
                AppSettingsState settings = AppSettingsState.getInstance();
                if ((ModelsSaveTime.getInstance().needRetraining(project) ||
                        !loadModelsOrIntellij(project, indicator)) && !settings.firstOpen && settings.automaticTraining) {
                    trainModelsForAllLanguages(project, indicator, true);
                }
            }
        });
    }

    public enum TrainingStatus {
        CANCELED_OR_FAILED,
        FULLY_COMPLETED,
        NON_FULLY_COMPLETED
    }
}

