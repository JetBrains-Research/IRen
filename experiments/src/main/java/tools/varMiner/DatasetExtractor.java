package tools.varMiner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.utils.DeprecatedPsiUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;
import static java.lang.Integer.min;
import static java.lang.Math.abs;
import static org.jetbrains.iren.utils.DeprecatedPsiUtils.findReferences;
import static org.jetbrains.iren.utils.DeprecatedPsiUtils.processToken;

public class DatasetExtractor {
    public static final String TOKEN_DELIMITER = "\u2581";
    public static int NGramLengthBeforeUsage = 20;
    public static int NGramLengthAfterUsage = 20;
    private static final Path datasetDir = Paths.get(PathManager.getSystemPath(), "dataset");

    public static void build(@NotNull Project project, @Nullable Path saveDir) {
        build(project, saveDir, null);
    }

    public static void build(@NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        build(project, null, progressIndicator);
    }

    public static void build(@NotNull Project project, @Nullable Path saveDir, @Nullable ProgressIndicator progressIndicator) {
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project));
        HashMap<String, List<VariableFeatures>> dataset = new HashMap<>();
        HashMap<String, Object> fileStats = new HashMap<>();
        int progress = 0;
        final int total = files.size();
        Instant start = Instant.now();
        @NotNull PsiManager psiManager = PsiManager.getInstance(project);
        System.out.printf("Number of files to parse: %s\n", files.size());
        for (VirtualFile file : files) {
            @Nullable PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
                @NotNull String filePath = file.getPath();
                dataset.put(filePath, DatasetExtractor.parsePsiFile(psiFile));
                fileStats.put(filePath, psiFile.getTextLength());
                double fraction = ++progress / (double) total;
                if (total < 100 || progress % (total / 100) == 0) {
                    Duration timeSpent = Duration.between(start, Instant.now());
                    Duration timeLeft = Duration.ofMillis((long) (timeSpent.toMillis() * (1 / fraction - 1)));
                    System.out.printf(
                            "Status: %.0f%%;\tTime spent: %s;\tTime left: %s\r",
                            fraction * 100.0,
                            timeSpent,
                            timeLeft.toString()
                    );
                }
                if (progressIndicator != null) {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setText2(file.getPath());
                    progressIndicator.setFraction(progress / (double) total);
                }
            } else {
                System.out.println("PSI isn't found");
            }
        }
        save(saveDir == null ? datasetDir : saveDir, project, dataset, fileStats);
        Instant end = Instant.now();
        Duration timeSpent = Duration.between(start, end);
        long minutes = timeSpent.toMinutes();
        int seconds = (int) (timeSpent.toMillis() / 1000. - 60. * minutes);
        System.out.printf("Done in %d min. %d s.\n",
                minutes, seconds);
    }

    private static List<VariableFeatures> parsePsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter(element -> element instanceof PsiVariable)
                .toList()
                .stream()
                .map(e -> (PsiVariable) e)
                .map(v -> getVariableFeatures(v, file))
                .collect(Collectors.toList());
    }

    public static VariableFeatures getVariableFeatures(PsiVariable variable, PsiFile file) {
        Stream<PsiReference> elementUsages = findReferences(variable, file);
        return new VariableFeatures(variable,
                Stream.concat(Stream.of(variable), elementUsages)
                        .map(DeprecatedPsiUtils::getIdentifier)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(PsiElement::getTextOffset))
                        .map(id -> getUsageFeatures(variable, id, file))
                        .collect(Collectors.toList()));
    }

    private static UsageFeatures getUsageFeatures(@NotNull PsiVariable variable, @NotNull PsiElement element, @NotNull PsiFile file) {
        List<String> tokens = new ArrayList<>();
//        Adding tokens before usage
        int order = NGramLengthBeforeUsage;
        for (PsiElement token : SyntaxTraverser
                .revPsiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, max(0, element.getTextOffset() - 1)))
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(DeprecatedPsiUtils::shouldLex)) {
            tokens.add(processToken(token, variable));
            if (--order < 1) {
                break;
            }
        }
        tokens = Lists.reverse(tokens);
//        Adding tokens after usage
        order = NGramLengthAfterUsage + 1;
        for (PsiElement token : SyntaxTraverser
                .psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(min(element.getTextOffset(), file.getTextLength()), file.getTextLength()))
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(DeprecatedPsiUtils::shouldLex)) {
            tokens.add(processToken(token, variable));
            if (--order < 1) {
                break;
            }
        }
        return new UsageFeatures(
                String.join(DatasetExtractor.TOKEN_DELIMITER, tokens),
                abs(variable.getTextOffset() - element.getTextOffset())
        );
    }

    private static void save(@NotNull Path saveDir, @NotNull Project project, @NotNull HashMap<String, List<VariableFeatures>> dataset) {
        save(saveDir, project, dataset, null);
    }

    private static void save(@NotNull Path saveDir, @NotNull Project project, @NotNull HashMap<String, List<VariableFeatures>> dataset, @Nullable HashMap<String, Object> fileStats) {
        File datasetFile = saveDir.resolve(project.getName() + "_dataset.json").toFile();
        File statsFile = saveDir.resolve(project.getName() + "_stats.json").toFile();
        try {
            datasetFile.getParentFile().mkdirs();
            datasetFile.createNewFile();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(datasetFile, dataset);
            if (fileStats != null) {
                statsFile.createNewFile();
                mapper.writeValue(statsFile, fileStats);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
