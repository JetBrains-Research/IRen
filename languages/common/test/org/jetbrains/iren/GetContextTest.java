package org.jetbrains.iren;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.storages.Context;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public abstract class GetContextTest extends ParsingTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "parsing";
    }

    @Override
    protected @NotNull String getTestFileNameResult() {
        return "/context/" + getTestName(true) + ".txt";
    }

    @Override
    public Object invokeSupporterFunction() {
        @NotNull LanguageSupporter supporter = getLanguageSupporter();
        final PsiElement variable = getTargetElementAtCaret();
        assertTrue(supporter.isVariableDeclarationOrReference(variable));
        return supporter.getContext((PsiNameIdentifierOwner) variable, false);
    }

    @Override
    public Object parseResult(String resultFileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(resultFileName))) {
            return Context.deserialize(br);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
