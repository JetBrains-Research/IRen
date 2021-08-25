package org.jetbrains.iren.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.iren.IdNamesSuggestingBundle;

public class NotificationsUtil {
    /**
     * Checks if intellij is in the "developer.mode" and then sends notification.
     */
    public static void notify(Project project, String title, String context) {
        if (isDeveloperMode()) {
            Notifications.Bus.notify(
                    new Notification(IdNamesSuggestingBundle.message("name"),
                            title,
                            context,
                            NotificationType.INFORMATION),
                    project);
        }
    }

    public static boolean isDeveloperMode(){
        return Registry.get("iren.developer.mode").asBoolean();
    }
}