package org.jetbrains.iren.test;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.structureView.impl.java.JavaLambdaNodeProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.api.VariableNamesContributor;
import org.jetbrains.iren.application.ProjectOpenCloseListener;
import org.jetbrains.iren.rename.IRenVariableInplaceRenameHandler;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.settings.AppSettingsState;

import static org.junit.Assert.assertNotNull;

public abstract class RefactoringTest extends IRenTestCase {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "refactoring";
    }

    @Override
    protected @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    public void doTestRenameUsingHandler(@Nullable String newName) {
        configureByFile(getTestFileNameBefore());
        renameElementAtCaretUsingHandler(newName);
        checkResultByFile(getTestFileNameAfter());
    }

    protected @NotNull String getTestFileNameBefore() {
        return "/before/" + getTestName(true) + getFileExtension();
    }

    private @NotNull String getTestFileNameAfter() {
        return "/after/" + getTestName(true) + getFileExtension();
    }

    public void renameElementAtCaretUsingHandler(@Nullable String newName) {
        @Nullable PsiElement element = TargetElementUtil.findTargetElement(
                getEditor(),
                TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        );
        assertNotNull(element);
        final DataContext context = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT, element, getCurrentEditorDataContext());
        VariableInplaceRenameHandler renameHandler = new IRenVariableInplaceRenameHandler();

        LanguageSupporter.getInstance(JavaLanguage.INSTANCE);

        if (newName == null) {
            assertFalse("In-place rename is allowed", renameHandler.isRenaming(context));
        } else {
            assertTrue("In-place rename not allowed", renameHandler.isRenaming(context));
            CodeInsightTestUtil.doInlineRename(renameHandler, newName, getEditor(), element);
        }
    }
}
