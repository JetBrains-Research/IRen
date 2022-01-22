package org.jetbrains.iren.training;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.jetbrains.iren.utils.NotificationsUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class MemoryListener implements Disposable {
    private final LowMemoryWatcher memoryWatcher;
    final AtomicBoolean canceled = new AtomicBoolean();

    public MemoryListener() {
        memoryWatcher = LowMemoryWatcher.register(this::showNotification,
                LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC);
    }

    private void showNotification() {
        if (canceled.compareAndSet(false, true)) {
            NotificationsUtil.notEnoughMemoryForTraining();
        }
    }

    public boolean shouldCancel() {
        return canceled.get();
    }

    @Override
    public void dispose() {
        memoryWatcher.stop();
    }
}
