package tools.graphVarMiner;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.ChunkWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.iren.utils.DeprecatedPsiUtils.processToken;
import static org.jetbrains.iren.utils.DeprecatedPsiUtils.shouldLex;

public class GraphDatasetExtractor {
    private static final String defaultPrefix = Paths.get(PathManager.getSystemPath(), "dataset", "graph").toString();
    private static final Logger log = Logger.getInstance(GraphDatasetExtractor.class);

    public static void build(@NotNull Project project, @Nullable String prefix) {
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project));
        int progress = 0;
        final int total = files.size();
        System.out.printf("Number of files to parse: %s\n", total);
        Instant start = Instant.now();

        @NotNull PsiManager psiManager = PsiManager.getInstance(project);
        ChunkWriter<SerializableVarData> writer = new ChunkWriter<>(prefix == null ? defaultPrefix : prefix,
                2000);
        for (VirtualFile file : files) {
//            System.out.println(file.getCanonicalPath());
            try {
                long fileSize = Files.size(Paths.get(file.getPath()));
                if (fileSize > 128 * 1024) {  // 128 KB
                    log.info(String.format("Skip file %s with size %dB", file.getPath(), fileSize));
                    continue;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            @Nullable PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {

                extractFromPsiFile(psiFile).forEach(writer::add);
//                Clear resolve cache
                ResolveCache.getInstance(project).clearCache(true);

                double fraction = ++progress / (double) total;
//                if (total < 100 || progress % (total / 100) == 0) {
                Duration timeSpent = Duration.between(start, Instant.now());
                Duration timeLeft = Duration.ofMillis((long) (timeSpent.toMillis() * (1 / fraction - 1)));
                System.out.printf(
                        "Status: %.0f%%;\tTime spent: %s;\tTime left: %s\r",
                        fraction * 100.0,
                        timeSpent,
                        timeLeft
                );
//                }
            } else {
                System.out.println("PSI isn't found");
            }
        }
        writer.close();

        Instant end = Instant.now();
        Duration timeSpent = Duration.between(start, end);
        System.out.printf("Done in %s\n", timeSpent);
    }

    public static class SerializableVarData {
        String filename;
        Graph.JsonSerializableGraph ContextGraph;
        String name;
        List<String> types;
        String span;
    }

    private static @NotNull Stream<SerializableVarData> extractFromPsiFile(@NotNull PsiFile file) {
//        Check: C:/Users/Igor/IdeaProjects/dataset/java-small/intellij-community/platform/analysis-impl/src/com/intellij/codeInspection/ex/InspectionProfileImpl.java
        try {
            JavaGraphExtractor graphExtractor = new JavaGraphExtractor(file);
            return SyntaxTraverser.psiTraverser()
                    .withRoot(file)
                    .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                    .filter(element -> element instanceof PsiVariable)
                    .toList()
                    .stream()
                    .map(e -> (PsiVariable) e)
                    .map(v -> getVarData(v, file, graphExtractor))
                    .filter(Objects::nonNull);
        } catch (Exception e) {
            log.error(String.format("Error while extracting from file: %s\n", file.getVirtualFile().getPath()));
            log.error(e);
            return Stream.empty();
        }
    }

    public static @Nullable SerializableVarData getVarData(@NotNull PsiVariable variable, @NotNull PsiFile file, @NotNull JavaGraphExtractor graphExtractor) {
        SerializableVarData varData = new SerializableVarData();
        Graph<PsiElement> graph = graphExtractor.createGraph(variable);
        if (graph == null) return null;
        try {
            varData.ContextGraph = graph.toJsonSerializableObject(element -> printNode(element, variable));
        } catch (IOException e) {
            return null;
        }
        varData.filename = file.getVirtualFile().getPath();
        varData.span = variable.getTextRange().toString();
        varData.name = variable.getName();
        PsiType varType = variable.getType();
        varData.types = Stream.concat(Stream.of(varType), Stream.of(varType.getSuperTypes()))
                .map(PsiType::getCanonicalText)
                .collect(Collectors.toList());
        return varData;
    }

    private static String printNode(@NotNull PsiElement element, @NotNull PsiVariable variable) {
        if (shouldLex(element)) {
            return processToken(element, variable);
        }
        String className = element.getClass().getSimpleName();
        return className.substring(3, className.length() - 4);
    }
}
