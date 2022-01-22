package org.jetbrains.iren;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public abstract class LexFileTest extends ParsingTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "parsing";
    }

    @Override
    protected @NotNull String getTestFileNameResult() {
        return "/lexed/" + getTestName(true) + ".txt";
    }

    @Override
    public Object invokeSupporterFunction() {
        @NotNull LanguageSupporter supporter = getLanguageSupporter();
        return supporter.lexPsiFile(getFile());
    }

    @Override
    public Object parseResult(String resultFileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(resultFileName))) {
            return List.of(br.readLine().split(" "));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
