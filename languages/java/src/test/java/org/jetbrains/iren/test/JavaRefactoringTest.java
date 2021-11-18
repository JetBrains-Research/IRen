package org.jetbrains.iren.test;

import com.intellij.ide.highlighter.JavaFileType;
import org.jetbrains.annotations.NotNull;

public class JavaRefactoringTest extends RefactoringTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "refactoring";
    }

    @Override
    protected @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    public void testLocalVariableRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testParameterRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testFieldRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testMethodRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testClassRefactoring() { doTestRenameUsingHandler("newName"); }
}
