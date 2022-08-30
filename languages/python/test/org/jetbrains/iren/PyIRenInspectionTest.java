package org.jetbrains.iren;

import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

public class PyIRenInspectionTest extends IRenInspectionTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + PythonFileType.INSTANCE.getDefaultExtension();
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new PyLanguageSupporter();
    }

    public void testInspection() {
        doTestInspection();
    }
}
