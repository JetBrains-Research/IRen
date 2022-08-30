package org.jetbrains.iren;

import com.intellij.ide.highlighter.JavaFileType;
import org.jetbrains.annotations.NotNull;

public class JavaIRenInspectionTest extends IRenInspectionTest {
    @Override
    public @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new JavaLanguageSupporter();
    }

    public void testInspection() {
        doTestInspection();
    }
}
