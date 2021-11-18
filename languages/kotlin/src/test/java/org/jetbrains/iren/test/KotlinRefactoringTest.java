package org.jetbrains.iren.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;

public class KotlinRefactoringTest extends RefactoringTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "refactoring";
    }

    @Override
    protected @NotNull String getFileExtension() {
        return "." + KotlinFileType.EXTENSION;
    }

    public void testPropertyRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testParameterRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testMethodRefactoring() { doTestRenameUsingHandler("newName"); }

    public void testClassRefactoring() { doTestRenameUsingHandler("newName"); }
}
