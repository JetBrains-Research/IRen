package org.jetbrains.iren.application;

import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.iren.utils.NotificationsUtil.askPermissions;

public class AskPermissionsPreloadingActivity extends PreloadingActivity {
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        askPermissions();
    }
}
