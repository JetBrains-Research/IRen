package org.jetbrains.iren;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.services.ConsistencyChecker;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class IRenInspectionTest extends LanguageSupporterTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ApplicationManager.getApplication().getExtensionArea().registerExtensionPoint(
                "org.jetbrains.iren.language.supporter",
                "org.jetbrains.iren.LanguageSupporter",
                ExtensionPoint.Kind.INTERFACE,
                true);
        LanguageSupporter.INSTANCE.getPoint().registerExtension(getLanguageSupporter(), ApplicationManager.getApplication());
    }

    protected void doTestInspection() {
        configureByFile(getTestFileName());
        final LanguageSupporter supporter = getLanguageSupporter();
        Set<PsiElement> trueHighlightedElements = getEditor().getCaretModel().getAllCarets().stream()
                .map(this::getTargetElementAtCaret)
                .collect(Collectors.toSet());
        PsiFile file = getFile();
        Set<PsiElement> highlightedElements = new HashSet<>();
        visitFile(file, supporter, highlightedElements);
        assertEquals(trueHighlightedElements, highlightedElements);
    }

    private static void visitFile(PsiFile file, LanguageSupporter supporter, Collection<PsiElement> results) {
        ConsistencyChecker checker = new ConsistencyChecker();
        new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiNameIdentifierOwner &&
                        supporter.isVariableDeclaration(element) &&
                        checker.isInconsistent((PsiNameIdentifierOwner) element)) {
                    results.add(element);
                }
                super.visitElement(element);
            }
        }.visitFile(file);
    }
}
