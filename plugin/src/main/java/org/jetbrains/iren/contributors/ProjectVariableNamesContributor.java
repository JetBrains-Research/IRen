package org.jetbrains.iren.contributors;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelRunner;
import org.jetbrains.iren.services.NGramModelManager;
import org.jetbrains.iren.services.NGramModelsUsabilityService;
import org.jetbrains.iren.utils.ModelUtils;

public class ProjectVariableNamesContributor extends NGramVariableNamesContributor {
    @Override
    protected boolean forgetFile() {
        return true;
    }

    @Override
    public @Nullable ModelRunner getModelRunnerToContribute(Project project, @NotNull PsiNameIdentifierOwner variable) {
        String name = new ModelUtils().getName(project, variable.getLanguage());
        if (NGramModelsUsabilityService.getInstance(project).isUsable(name)) {
            return NGramModelManager.getInstance(project).get(name);
        }
        return null;
    }
}
