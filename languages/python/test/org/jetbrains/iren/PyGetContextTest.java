package org.jetbrains.iren;

import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

public class PyGetContextTest extends GetContextTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + PythonFileType.INSTANCE.getDefaultExtension();
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new PyLanguageSupporter();
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
