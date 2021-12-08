package org.jetbrains.iren.utils;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* Copied necessary from org.jetbrains.idea.devkit.util.PsiUtil
*/
public class PsiUtil {
    private static final Key<Boolean> IDEA_PROJECT = Key.create("idea.internal.inspections.enabled");
    private static final List<String> IDEA_PROJECT_MARKER_MODULE_NAMES =
            ContainerUtil.immutableList("intellij.idea.community.main", "intellij.platform.commercial");
    private static final @NonNls String IDE_PROJECT_MARKER_CLASS = JBList.class.getName();

    public static boolean isIdeaProject(@NotNull Project project) {
        Boolean flag = project.getUserData(IDEA_PROJECT);
        if (flag == null) {
            flag = checkIdeaProject(project);
            project.putUserData(IDEA_PROJECT, flag);
        }

        return flag;
    }

    private static boolean checkIdeaProject(@NotNull Project project) {
        boolean foundMarkerModule = false;
        for (String moduleName : IDEA_PROJECT_MARKER_MODULE_NAMES) {
            if (ModuleManager.getInstance(project).findModuleByName(moduleName) != null) {
                foundMarkerModule = true;
                break;
            }
        }
        if (!foundMarkerModule) return false;

        return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> {
            GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project);
            return ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findClass(IDE_PROJECT_MARKER_CLASS, scope)) != null;
        });
    }
}
