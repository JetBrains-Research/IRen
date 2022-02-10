package org.jetbrains.iren.utils;

import com.intellij.lang.Language;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ModelUtils {
    public static final Path MODELS_DIRECTORY = Paths.get(PathManager.getSystemPath(), "models");
    public static final String INTELLIJ_NAME = "intellij";
    public static final String CURRENT_MODEL_VERSION = "2";

    public static @NotNull Path getPath(@NotNull String name) {
        return MODELS_DIRECTORY.resolve(name);
    }

    public static @NotNull String getName(@NotNull Project project,
                                   @Nullable Language language) {
        return (IdeaUtil.isIdeaProject(project) ?
                INTELLIJ_NAME : String.join("_", project.getName(), project.getLocationHash())
        ) + (language == null ? "" : String.join("_", "", language.getID(), CURRENT_MODEL_VERSION));
    }

    public static boolean deleteOldModels() {
        AtomicBoolean res = new AtomicBoolean(false);
        if (Files.exists(ModelUtils.MODELS_DIRECTORY)) {
            try (Stream<Path> paths = Files.list(ModelUtils.MODELS_DIRECTORY)) {
                paths
                        .filter(Files::isDirectory)
                        .filter(ModelUtils::isNotCurrentVersion)
                        .map(Path::toFile)
                        .forEach(file -> {
                            try {
                                FileUtils.deleteDirectory(file);
                                res.set(true);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return res.get();
    }

    private static boolean isNotCurrentVersion(Path path) {
        return !path.toString().endsWith(CURRENT_MODEL_VERSION);
    }
}
