package org.jetbrains.iren;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.utils.LanguageSupporter;

import static org.jetbrains.iren.utils.NotificationsUtil.askPermissions;

public class PluginLoadedListener implements DynamicPluginListener {
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        DynamicPluginListener.super.beforePluginUnload(pluginDescriptor, isUpdate);
        Disposer.dispose(ModelManager.getInstance());
        Disposer.dispose(ConsistencyChecker.getInstance());
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        DynamicPluginListener.super.pluginLoaded(pluginDescriptor);
        LanguageSupporter.removeRenameHandlers();
        askPermissions();
    }
}
