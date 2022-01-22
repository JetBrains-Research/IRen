package org.jetbrains.iren.services;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

public interface RenameHistory {
    static @NotNull RenameHistory getInstance(Project project) {
        return project.getService(RenameHistory.class);
    }

    void rememberVariableName(@NotNull String variableHash, @NotNull String insertedName);

    boolean isRenamedVariable(@NotNull PsiNameIdentifierOwner variable);

    String getVariableHash(PsiNamedElement variable, boolean insertName);
}
