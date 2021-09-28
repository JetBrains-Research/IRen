package org.jetbrains.iren.utils;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public interface LanguageSupporter {
    ExtensionPointName<LanguageSupporter> INSTANCE = ExtensionPointName.create("org.jetbrains.iren.language.supporter");

    static LanguageSupporter getInstance(Language language) {
        return INSTANCE.extensions().filter(x -> x.getLanguage() == language).findFirst().orElse(null);
    }

    @NotNull Language getLanguage();

    @NotNull FileType getFileType();

    @NotNull Collection<Class<? extends PsiNameIdentifierOwner>> getVariableClasses();

    static boolean hasSupportedFiles(Project project) {
        return INSTANCE.extensions().anyMatch(supporter ->
                FileTypeIndex.containsFileOfType(supporter.getFileType(),
                        GlobalSearchScope.projectScope(project)));
    }

    static void removeRenameHandlers() {
        INSTANCE.extensions().forEach(LanguageSupporter::removeHandlers);
    }

    void removeHandlers();

    /**
     * Checks if there is variables with newName in the scope.
     *
     * @param element element for which we suggest newName.
     * @param newName new name of the {@code element}.
     * @return if there are collisions.
     */
    boolean isColliding(@NotNull PsiElement element, @NotNull String newName);

    /**
     * @param variable        for which to find a context.
     * @param changeToUnknown if true, {@link Context} instance doesn't contain information
     *                        about ground truth name of the {@code variable}.
     * @return context of the variable.
     */
    @NotNull Context<String> getContext(@NotNull PsiNameIdentifierOwner variable, boolean changeToUnknown);

    @NotNull List<String> lexPsiFile(@NotNull PsiFile file);

    @NotNull List<String> lexPsiFile(@NotNull PsiFile file, @Nullable Consumer<PsiElement> consumer);

    boolean isVariable(@Nullable PsiElement token);

    boolean isVariableDeclaration(@Nullable PsiElement token);

    boolean isIdentifier(PsiElement token);

    /**
     * Compare execution time of a chosen snippet of the code.
     */
    static void showAvgTime() {
        INSTANCE.extensions().forEach(LanguageSupporter::printAvgTime);
    }

    void printAvgTime();
}
