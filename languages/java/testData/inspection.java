// carets define highlighted elements
package org.jetbrains.iren;

import java.io.IOError;

public class Foo {
    public void boo(int <caret>boi) {
        var <caret>sch = getSomeSchoolNumber();
        var state = Foo.computeSmthg();
        var component = returnMy();
        var answerForTheUltimateQuestionOfLife = computeAnswerForTheUltimateQuestionOfLife();
        var school = getSomeSchoolNumber();
        try {
            System.out.println("Oh My!");
        } catch (RuntimeException ignore) {
        } catch (IOError ignored) {
        } catch (Exception <caret>x) {
        }
    }

    private static State computeSmthg() {
        return new State();
    }

    private int computeAnswerForTheUltimateQuestionOfLife() {
        return 42;
    }

    public int getSomeSchoolNumber(int number<caret>) {
        return 239;
    }

    public MyComponent returnMy() {
        return new MyComponent();
    }

    static class State {
    }

    static class MyComponent {
    }
}

class Foo2 extends Foo {
    @java.lang.Override
    public void boo(int boi) {
        System.out.println("Hello World!");
    }

    @java.lang.Override
    public int getSomeSchoolNumber(int <caret>smth) {
        return 777;
    }
}
