package org.jetbrains.iren;

import com.intellij.ide.highlighter.JavaFileType;
import org.jetbrains.annotations.NotNull;

public class JavaGetContextTest extends GetContextTest {
    @Override
    public @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new JavaLanguageSupporter();
    }

    public void testLocalVariable() {
        doTestSupporterFunction();
    }

    public void testParameter() {
        doTestSupporterFunction();
    }

    public void testField() {
        doTestSupporterFunction();
    }
}
