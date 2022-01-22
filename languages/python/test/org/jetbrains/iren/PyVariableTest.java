package org.jetbrains.iren;

import com.jetbrains.python.PythonFileType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class PyVariableTest extends VariableTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + PythonFileType.INSTANCE.getDefaultExtension();
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new PyLanguageSupporter();
    }

    @Test
    @Parameters({"0", "1"})
    public void testNotVariable(int index) {
        doTestNotVariable(index);
    }

    @Test
    @Parameters({"0", "1"})
    public void testVariableDeclaration(int index) {
        doTestVariableDeclaration(index, true);
    }

    @Test
    @Parameters({"0", "1", "2"})
    public void testNotVariableDeclaration(int index) {
        doTestVariableDeclaration(index,false);
    }
}
