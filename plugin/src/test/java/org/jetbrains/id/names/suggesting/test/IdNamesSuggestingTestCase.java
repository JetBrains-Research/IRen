package org.jetbrains.id.names.suggesting.test;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class IdNamesSuggestingTestCase extends BasePlatformTestCase {
    protected static final String JAVA_FILE_EXTENSION = "." + JavaFileType.DEFAULT_EXTENSION;

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/" + getTestDataBasePath();
    }

    protected @NotNull String getTestDataBasePath() {
        return "";
    }

    protected void configureByFile() {
        myFixture.configureByFile(getTestFileName());
    }

    protected @NotNull String getTestFileName() {
        return getTestName(true) + JAVA_FILE_EXTENSION;
    }
}
