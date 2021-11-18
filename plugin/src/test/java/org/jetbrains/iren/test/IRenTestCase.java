package org.jetbrains.iren.test;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class IRenTestCase extends LightPlatformCodeInsightTestCase {
    @Override
    protected @NotNull String getTestDataPath() {
        return "src/test/testData/" + getTestDataBasePath();
    }

    protected @NotNull String getTestDataBasePath() {
        return "";
    }

    protected @NotNull String getTestFileName() {
        return getTestName(true) + getFileExtension();
    }

    protected abstract @NotNull String getFileExtension();
}
