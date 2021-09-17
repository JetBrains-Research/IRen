package org.jetbrains.iren.rename;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface DefaultHandlersRemover {
    ExtensionPointName<DefaultHandlersRemover> EP_NAME =
            ExtensionPointName.create("org.jetbrains.iren.defaultHandlersRemover");

    static void remove() {
        EP_NAME.extensions().forEach(DefaultHandlersRemover::removeHandlers);
    }

    void removeHandlers();
}
