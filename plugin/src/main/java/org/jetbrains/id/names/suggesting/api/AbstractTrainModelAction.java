package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractTrainModelAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (canBePerformed(e)) {
            doActionPerformed(e);
        }
    }

    protected abstract void doActionPerformed(@NotNull AnActionEvent e);

    protected abstract boolean canBePerformed(@NotNull AnActionEvent e);
}
