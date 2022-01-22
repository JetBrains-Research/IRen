package org.jetbrains.iren;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;

public class KotlinGetContextTest extends GetContextTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + KotlinFileType.EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new KotlinLanguageSupporter();
    }

    public void testProperty() { doTestSupporterFunction(); }

    public void testParameter() { doTestSupporterFunction(); }

    public void testLocalVariable() { doTestSupporterFunction(); }
}
