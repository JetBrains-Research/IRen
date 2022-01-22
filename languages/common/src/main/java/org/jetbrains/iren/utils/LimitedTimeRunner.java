package org.jetbrains.iren.utils;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LimitedTimeRunner {
    public static <T> @Nullable T runForSomeTime(long runningTimeMs, @NotNull Computable<T> process) {
        try {
            return ProgressManager.getInstance().runProcess(process, new LimitedRunningTimeIndicator(runningTimeMs));
        } catch (ProcessCanceledException e) {
//            System.out.println("Canceled");
            return null;
        }
    }
}

class LimitedRunningTimeIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
    final long startTime = System.currentTimeMillis();
    private final long runningTimeMs;

    public LimitedRunningTimeIndicator(long runningTimeMs) {
        this.runningTimeMs = runningTimeMs;
    }

    @Override
    public boolean isCanceled() {
        if (super.isCanceled()) {
            return true;
        }
        return (System.currentTimeMillis() - startTime) > runningTimeMs;
    }
}
