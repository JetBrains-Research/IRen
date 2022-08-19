package org.jetbrains.iren.application;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.services.NGramModelManager;

import static org.jetbrains.iren.utils.NotificationsUtil.askPermissions;

public class PluginLoadedListener implements DynamicPluginListener {
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        DynamicPluginListener.super.beforePluginUnload(pluginDescriptor, isUpdate);
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            Disposer.dispose(NGramModelManager.getInstance(project));
            Disposer.dispose(ConsistencyChecker.getInstance(project));
        }
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        DynamicPluginListener.super.pluginLoaded(pluginDescriptor);
        LanguageSupporter.removeRenameHandlers();
        askPermissions();
    }
}
