package org.jetbrains.iren.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.iren.IRenBundle;

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
}