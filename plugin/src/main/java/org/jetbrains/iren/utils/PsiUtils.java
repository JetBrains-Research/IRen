package org.jetbrains.iren.utils;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;

import java.util.List;
import java.util.function.Consumer;

public interface PsiUtils {
    ExtensionPointName<PsiUtils> INSTANCE = ExtensionPointName.create("org.jetbrains.iren.language.psiUtils");

    static PsiUtils getInstance(Language language) {
        return INSTANCE.extensions().filter(x -> x.getLanguage() == language).findFirst().orElse(null);
    }

    Language getLanguage();

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
}
