package org.jetbrains.iren.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.ModelBuilder;
import org.jetbrains.iren.settings.AppSettingsState;

import java.nio.file.Path;

import static com.intellij.ide.actions.RevealFileAction.openDirectory;

public class NotificationsUtil {
    /**
     * Checks if intellij is in the "developer.mode" and then sends notification.
     */
    public static void notify(Project project, String title, String context) {
        if (isVerboseInference()) {
            Notifications.Bus.notify(
                    new Notification(IRenBundle.message("name"),
                            title,
                            context,
                            NotificationType.INFORMATION),
                    project);
        }
    }

    public static boolean isVerboseInference() {
        return Registry.get("iren.verbose.inference").asBoolean();
    }

    public static void notificationAboutModel(@NotNull Project project, @NotNull String title, @NotNull String text, @Nullable Path modelPath) {
        final Notification notification = new Notification(IRenBundle.message("name"),
                title,
                text,
                NotificationType.INFORMATION);
        if (modelPath != null) notification.addAction(new NotificationAction("Open model's directory") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                openDirectory(modelPath);
            }
        });
        Notifications.Bus.notify(notification, project);
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
                    ModelBuilder.trainModelsForAllProjectsInBackground();
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