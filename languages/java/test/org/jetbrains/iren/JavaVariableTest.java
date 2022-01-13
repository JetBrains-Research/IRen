package org.jetbrains.iren;

import com.intellij.ide.highlighter.JavaFileType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.language.JavaLanguageSupporter;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class JavaVariableTest extends VariableTest {
    @Override
    public @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new JavaLanguageSupporter();
    }

    @Test
    @Parameters({"0", "1"})
    public void testNotVariable(int index) {
        doTestNotVariable(index);
    }

    @Test
    @Parameters({"0", "1", "2", "3", "4"})
    public void testVariableDeclaration(int index) {
        doTestVariableDeclaration(index, true);
    }

    @Test
    @Parameters({"0", "1", "2", "3", "4", "5", "6"})
    public void testNotVariableDeclaration(int index) {
        doTestVariableDeclaration(index,false);
    }
}
