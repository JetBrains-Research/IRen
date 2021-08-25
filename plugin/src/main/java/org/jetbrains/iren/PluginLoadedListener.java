package org.jetbrains.iren;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.inspections.variable.PredictionsStorage;
import org.jetbrains.iren.settings.AppSettingsState;

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
        askPermissions();
    }

    public static void askPermissions() {
        AppSettingsState settings = AppSettingsState.getInstance();
        if (settings.firstOpen) {
            Notification notification1 = new Notification(IdNamesSuggestingBundle.message("name"),
                    "IRen: send anonymous statistics",
                    "Do You allow sending anonymous statistics? We will only collect variable name predictions.",
                    NotificationType.INFORMATION);
            notification1.addAction(new NotificationAction("Yes (default)") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    settings.sendStatistics = true;
                    notification.expire();
                }
            });
            notification1.addAction(new NotificationAction("No") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    settings.sendStatistics = false;
                    notification.expire();
                }
            });
            Notifications.Bus.notify(notification1);

            Notification notification2 = new Notification(IdNamesSuggestingBundle.message("name"),
                    "IRen: automatic training permission",
                    "Do You allow automatic training of models?",
                    NotificationType.INFORMATION);
            notification2.addAction(new NotificationAction("Yes (default)") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    settings.automaticTraining = true;
                    notification.expire();
                    @Nullable Project project = e.getProject();
                    if (project == null) return;
                    ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("loading.project.model", project.getName())) {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            indicator.setText(IdNamesSuggestingBundle.message("training.progress.indicator.text", project.getName()));
                            ReadAction.nonBlocking(() -> ModelTrainer.trainProjectNGramModel(project, indicator, true))
                                    .inSmartMode(project).executeSynchronously();
                        }
                    });
                }
            });
            notification2.addAction(new NotificationAction("No") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    settings.automaticTraining = false;
                    notification.expire();
                }
            });
            Notifications.Bus.notify(notification2);

            settings.firstOpen = false;
        }
    }
}
