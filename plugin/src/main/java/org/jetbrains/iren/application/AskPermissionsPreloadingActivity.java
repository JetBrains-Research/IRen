package org.jetbrains.iren.application;

import com.intellij.openapi.application.PreloadingActivity;

import static org.jetbrains.iren.utils.NotificationsUtil.askPermissions;

@SuppressWarnings("UnstableApiUsage")
public class AskPermissionsPreloadingActivity extends PreloadingActivity {
    @Override
    public void preload() {
        askPermissions();
    }
}
