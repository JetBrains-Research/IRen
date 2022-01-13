package org.jetbrains.iren;

import org.jetbrains.annotations.NotNull;

public abstract class ParsingTest extends LanguageSupporterTest {
    protected void doTestSupporterFunction() {
        configureByFile(getTestFileNameCode());
        Object actual = invokeSupporterFunction();
        Object expected = parseResult(getTestDataPath() + getTestFileNameResult());
        assertEquals(expected, actual);
    }

    protected @NotNull String getTestFileNameCode() {
        return "/code/" + getTestFileName();
    }

    protected @NotNull String getTestFileNameResult() {
        return "/result/" + getTestName(true) + ".txt";
    }

    public abstract Object invokeSupporterFunction();

    public abstract Object parseResult(String resultFileName);

}
