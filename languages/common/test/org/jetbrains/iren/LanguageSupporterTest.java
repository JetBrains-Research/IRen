package org.jetbrains.iren;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LanguageSupporterTest extends LightPlatformCodeInsightTestCase {
    public @NotNull PsiElement getTargetElementAtCaret() {
        @Nullable PsiElement element = TargetElementUtil.findTargetElement(
                getEditor(),
                TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        );
        assertNotNull(element);
        return element;
    }

    @NotNull
    public PsiElement getTargetElementAtCaret(Caret caret) {
        final PsiElement element = new TargetElementUtil().findTargetElement(
                getEditor(),
                TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED,
                caret.getOffset()
        );
        assertNotNull(element);
        return element;
    }

    @Override
    protected @NotNull String getTestDataPath() {
        return "testData/" + getTestDataBasePath();
    }

    protected @NotNull String getTestFileName() {
        return getTestName(true)/* for parametrized tests */.split("\\(")[0] + getFileExtension();
    }

    protected abstract @NotNull String getTestDataBasePath();

    public abstract @NotNull String getFileExtension();

    public abstract @NotNull LanguageSupporter getLanguageSupporter();
}
