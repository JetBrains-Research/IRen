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
    @NotNull
    public String getModelsDirectoryName() {
        return "models";
    }

    @NotNull
    public String getVersion() {
        return "3";
    }

    public final Path modelsDirectory = Paths.get(PathManager.getSystemPath(), getModelsDirectoryName());

    public static final String INTELLIJ_NAME = "intellij";

    public @NotNull Path getPath(@NotNull String name) {
        return modelsDirectory.resolve(name);
    }

    public @NotNull String getName(@NotNull Project project,
                                   @Nullable Language language) {
        return (IdeaUtil.isIdeaProject(project) ?
                INTELLIJ_NAME : String.join("_", project.getName(), project.getLocationHash())
        ) + (language == null ? "" : String.join("_", "", language.getID(), getVersion()));
    }

    public boolean deleteOldModels() {
        AtomicBoolean res = new AtomicBoolean(false);
        if (Files.exists(modelsDirectory)) {
            try (Stream<Path> paths = Files.list(modelsDirectory)) {
                paths
                        .filter(Files::isDirectory)
                        .filter(this::isNotCurrentVersion)
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

    private boolean isNotCurrentVersion(Path path) {
        return !path.toString().endsWith(getVersion());
    }
}
