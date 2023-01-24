package org.jetbrains.iren.training;

import com.intellij.history.core.Paths;
import com.intellij.lang.Language;
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
import org.jetbrains.iren.DOBFModelRunner;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.ModelRunner;
import org.jetbrains.iren.models.OrtModelRunner;
import org.jetbrains.iren.ngram.NGramModelRunner;
import org.jetbrains.iren.ngram.PersistentNGramModelRunner;
import org.jetbrains.iren.services.*;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.storages.StringCounter;
import org.jetbrains.iren.storages.Vocabulary;
import org.jetbrains.iren.utils.DOBFModelUtils;
import org.jetbrains.iren.utils.ModelUtils;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.jetbrains.iren.ModelLoaderKt.downloadAndExtractModel;
import static org.jetbrains.iren.training.ModelBuilder.TrainingStatus.*;
import static org.jetbrains.iren.utils.IdeaUtil.isIdeaProject;
import static org.jetbrains.iren.utils.ModelUtils.INTELLIJ_NAME;

public class ModelBuilder {
    private static final Logger LOG = Logger.getInstance(ModelBuilder.class);
    private final Project myProject;
    private final LanguageSupporter mySupporter;
    private final ProgressIndicator myProgressIndicator;
    private final int vocabularyCutOff = AppSettingsState.getInstance().getVocabularyCutOff();
    private final int maxTrainingTime = AppSettingsState.getInstance().getMaxTrainingTime();
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
            loadNGramModelsOrIntellij(project, progressIndicator);
            return;
        }
        progressIndicator.setText(IRenBundle.message("training.progress.indexing"));
//            Waits until indexes are prepared
        DumbService.getInstance(project).waitForSmartMode();
        @NotNull NGramModelsUsabilityService usabilityService = NGramModelsUsabilityService.getInstance(project);
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
            ConsistencyChecker.getInstance(project).dispose();
        }
    }

    private static void trainWithSupporter(Project project, LanguageSupporter supporter, ProgressIndicator progressIndicator, boolean save) {
        new ModelBuilder(project, supporter, progressIndicator).train(save);
    }

    private void train(boolean save) {
        ModelUtils modelUtils = new ModelUtils();
        String name = modelUtils.getName(myProject, mySupporter.getLanguage());
        NGramModelsUsabilityService.getInstance(myProject).setUsable(name, false);
        NGramModelManager.getInstance(myProject).remove(name);
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
            final Path modelPath = modelUtils.getPath(name);
            modelRunner = new PersistentNGramModelRunner(modelRunner);
            modelSize = modelRunner.save(modelPath, myProgressIndicator);
            if (modelSize <= 0 || !modelRunner.load(modelPath, myProgressIndicator)) return;
            System.out.println(IRenBundle.message("model.size", modelSize));
        }
        NotificationsUtil.modelTrained(myProject,
                mySupporter,
                status == FULLY_COMPLETED,
                start,
                modelRunner.getVocabulary().size(),
                modelSize);
        NGramModelManager.getInstance(myProject).put(name, modelRunner);
        NGramModelsUsabilityService.getInstance(myProject).setUsable(name, true);
    }

    public TrainingStatus trainModelRunner(@NotNull ModelRunner modelRunner) {
        modelRunner.train();
        if (myProgressIndicator != null) {
            myProgressIndicator.setIndeterminate(false);
        }
        final Collection<VirtualFile> files = getProjectFilesNotExcluded();
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
                    counter.putAll(mySupporter.lexPsiFile(psiFile));
                    viewedFiles.add(file);
                });
        files.clear();
        files.addAll(viewedFiles);
        vocabulary.clear();
        counter.toVocabulary(vocabulary, vocabularyCutOff);
        System.out.printf("Done in %s\n", Duration.between(trainingStart, Instant.now()));
    }

    private boolean trainingIsNotCanceled(Instant trainingStart, int maxTrainingTime) {
        return !myProject.isDisposed() && !memoryListener.shouldCancel() && (myProgressIndicator == null || !myProgressIndicator.isCanceled()) &&
                (maxTrainingTime <= 0 || Duration.between(trainingStart, Instant.now())
                        .minusSeconds(maxTrainingTime).isNegative());
    }

    public @NotNull TrainingStatus trainNGramModel(@NotNull ModelRunner modelRunner,
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

    private Collection<VirtualFile> getProjectFilesNotExcluded() {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
            return ReadAction.compute(() -> FileTypeIndex.getFiles(mySupporter.getFileType(),
                    GlobalSearchScope.projectScope(myProject)));
        }
        return ReadAction.compute(() -> FileTypeIndex.getFiles(mySupporter.getFileType(),
                        GlobalSearchScope.projectScope(myProject))
                .stream()
                .filter(file -> !ProjectFileIndex.getInstance(myProject).isExcluded(file))
                .collect(Collectors.toList()));
    }

    private static boolean loadNGramModelsOrIntellij(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        if (isIdeaProject(project)) {
            LOG.info(String.format("Project %s was defined as intellij", project.getName()));
            if (NGramModelManager.getInstance(project).containsIntellijModel() || loadNGramModels(project, indicator))
                return true;
            final AppSettingsState settings = AppSettingsState.getInstance();
            if (!settings.getFirstOpen() && settings.getAutomaticTraining()) downloadAndExtractModel(indicator,
                    INTELLIJ_NAME,
                    new ModelUtils().modelsDirectory);
        }
        return loadNGramModels(project, indicator);
    }

    private static boolean loadNGramModels(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        boolean isSmthngLoaded = false;
        ModelUtils modelUtils = new ModelUtils();
        for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
            indicator.setText(IRenBundle.message("loading.text", project.getName(), supporter.getLanguage().getDisplayName()));
            String name = modelUtils.getName(project, supporter.getLanguage());
            ModelRunner modelRunner = new PersistentNGramModelRunner();
            Instant start = Instant.now();
            final Path modelPath = modelUtils.getPath(name);
            boolean isLoaded = modelRunner.load(modelPath, indicator);
//            Don't want to save a link to the model if the project is disposed
            if (project.isDisposed()) {
                indicator.cancel();
                break;
            }
            isSmthngLoaded |= isLoaded;
            if (isLoaded) {
                modelRunner.getVocabulary().close();
                NGramModelManager.getInstance(project).put(name, modelRunner);
                NGramModelsUsabilityService.getInstance(project).setUsable(name, true);
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

    private static boolean loadDOBFModels(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        boolean isSmthngLoaded = false;
        DOBFModelUtils modelUtils = new DOBFModelUtils();
        for (LanguageSupporter supporter : LanguageSupporter.INSTANCE.getExtensionList()) {
            if (!supporter.dobfReady()) continue;
            Language language = supporter.getLanguage();
            indicator.setText(IRenBundle.message("dobf.model.loading", language.getDisplayName()));
            String name = modelUtils.getName(language);
            Instant start = Instant.now();
            final Path modelPath = modelUtils.getPath(name);
            if (!modelPath.toFile().exists()) downloadAndExtractModel(indicator, name, modelUtils.modelsDirectory);
            DOBFModelRunner modelRunner = new OrtModelRunner(modelPath, 512, 1024);
            isSmthngLoaded = true;
            DOBFModelManager.Companion.getInstance().put(language, modelRunner);
            NotificationsUtil.notificationAboutModel(project,
                    IRenBundle.message("dobf.model.loaded"),
                    IRenBundle.message("dobf.model.loading.statistics",
                            language.getDisplayName(),
                            Duration.between(start, Instant.now()).toSeconds()),
                    modelPath);
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
                if (new ModelUtils().deleteOldModels() || new DOBFModelUtils().deleteOldModels())
                    NotificationsUtil.oldModelsDeleted();
                loadDOBFModels(project, indicator);
                AppSettingsState settings = AppSettingsState.getInstance();
                if ((ModelsSaveTime.getInstance().needRetraining(project) ||
                        !loadNGramModelsOrIntellij(project, indicator)) && !settings.getFirstOpen() && settings.getAutomaticTraining()) {
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

