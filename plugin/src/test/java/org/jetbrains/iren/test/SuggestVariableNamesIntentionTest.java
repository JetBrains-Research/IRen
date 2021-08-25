package org.jetbrains.iren.test;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.IdNamesSuggestingBundle;
import org.jetbrains.iren.impl.TrainProjectNGramModelAction;

public class SuggestVariableNamesIntentionTest extends IdNamesSuggestingTestCase {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "intention";
    }

    public void testIntentionIsAvailableOnDeclarationLeft() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnDeclarationRight() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnVariableLeft() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnVariableRight() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnParameter() { doTestIntentionIsAvailable(); }

    public void testIntentionIsNotAvailable1() { doTestIntentionIsNotAvailable(); }

    public void testIntentionIsNotAvailable2() { doTestIntentionIsNotAvailable(); }

    public void testIntentionIsNotAvailable3() { doTestIntentionIsNotAvailable(); }

    private void doTestIntentionIsAvailable() {
        configureByFile();
        myFixture.testAction(new TrainProjectNGramModelAction());
        assertContainsElements(ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText),
                IdNamesSuggestingBundle.message("intention.text"));
    }

    private void doTestIntentionIsNotAvailable() {
        configureByFile();
        myFixture.testAction(new TrainProjectNGramModelAction());
        assertDoesntContain(ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText),
                IdNamesSuggestingBundle.message("intention.text"));
    }
}
