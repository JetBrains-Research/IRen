package org.jetbrains.iren;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static org.jetbrains.iren.utils.StringUtils.areSubtokensMatch;

public interface LanguageSupporter {
    ExtensionPointName<LanguageSupporter> INSTANCE = ExtensionPointName.create("org.jetbrains.iren.language.supporter");

    static @Nullable LanguageSupporter getInstance(Language language) {
//        TODO: think about how to better handle unsupported languages, mb inform about it in logs...
        return INSTANCE.extensions().filter(x -> language.isKindOf(x.getLanguage())).findFirst().orElse(null);
    }

    @NotNull Language getLanguage();

    @NotNull FileType getFileType();

    @NotNull IElementType getIdentifierType();

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
     * @param forWholeFile    parse the whole file.
     * @param changeToUnknown if true, {@link Context} instance doesn't contain information
     *                        about ground truth name of the {@code variable}.
     * @param processTokens   replace string literals, numbers, etc.
     * @return context of the variable.
     */
    @Nullable Context<String> getContext(@NotNull PsiNameIdentifierOwner variable,
                                         boolean forWholeFile,
                                         boolean changeToUnknown,
                                         boolean processTokens);

    @NotNull List<String> lexPsiFile(@NotNull PsiFile file);

    @NotNull List<String> lexPsiFile(@NotNull PsiFile file, @Nullable Consumer<PsiElement> consumer);

    boolean isVariableDeclarationOrReference(@Nullable PsiElement token);

    boolean identifierIsVariableDeclaration(@Nullable PsiElement identifier);

    @Contract("null -> false")
    boolean isIdentifier(@Nullable PsiElement token);

    /**
     * Compare execution time of a chosen snippet of the code.
     */
    static void showAvgTime() {
        INSTANCE.extensions().forEach(LanguageSupporter::printAvgTime);
    }

    void printAvgTime();

    static @NotNull PsiElementVisitor getVariableVisitor(@NotNull Language language, @NotNull ProblemsHolder holder) {
        LanguageSupporter supporter = getInstance(language);
        return supporter == null ? PsiElementVisitor.EMPTY_VISITOR : supporter.createVariableVisitor(holder);
    }

    @NotNull PsiElementVisitor createVariableVisitor(@NotNull ProblemsHolder holder);

    boolean isStopName(@NotNull String name);

    boolean isVariableDeclaration(@Nullable PsiElement element);

    boolean excludeFromInspection(@NotNull PsiNameIdentifierOwner variable);

    @NotNull Collection<String> getDefaultSuggestions(@NotNull PsiNameIdentifierOwner variable);

    default boolean isInplaceRenameAvailable(PsiNamedElement elementToRename) {
        return true;
    }

    boolean isFunctionOrClass(PsiNameIdentifierOwner element);

    default boolean dobfReady() {
        return false;
    }

    boolean fastHighlighting(Project project, @NotNull PsiNameIdentifierOwner variable);

    boolean slowHighlighting(Project project, @NotNull PsiNameIdentifierOwner variable);
}
