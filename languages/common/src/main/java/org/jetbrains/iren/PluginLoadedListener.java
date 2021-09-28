package org.jetbrains.iren;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.inspections.variable.PredictionsStorage;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.utils.LanguageSupporter;

public class PluginLoadedListener implements DynamicPluginListener {
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        DynamicPluginListener.super.beforePluginUnload(pluginDescriptor, isUpdate);
        Disposer.dispose(ModelManager.getInstance());
        Disposer.dispose(PredictionsStorage.Companion.getInstance());
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        DynamicPluginListener.super.pluginLoaded(pluginDescriptor);
        LanguageSupporter.removeRenameHandlers();
        askPermissions();
    }

    public static void askPermissions() {
        AppSettingsState settings = AppSettingsState.getInstance();
        if (settings.firstOpen) {
            Notification notification = new Notification(IRenBundle.message("name"),
                    "IRen: automatic training permission",
                    "Do You allow automatic training of models?",
                    NotificationType.INFORMATION);
            notification.addAction(new NotificationAction("Yes (default)") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    settings.automaticTraining = true;
                    settings.firstOpen = false;
                    notification.expire();
                    @Nullable Project project = e.getProject();
                    if (project == null) return;
                    ModelTrainer.trainProjectNGramModelInBackground(project);
                }
            });
            notification.addAction(new NotificationAction("No") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    settings.automaticTraining = false;
                    settings.firstOpen = false;
                    notification.expire();
                }
            });
            Notifications.Bus.notify(notification);
        }
    }
}
