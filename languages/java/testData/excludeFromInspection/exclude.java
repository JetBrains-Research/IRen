package testData.excludeFromInspection;

public class Foo {
    public void boo(int boi) {
        var sta<caret>te = Foo.computeSmthg();
        var compo<caret>nent = returnMy();
        var answerForTheUltima<caret>teQuestionOfLife = computeAnswerForTheUltimateQuestionOfLife();
        var s<caret>chool = getSomeSchoolNumber();
        try {
            System.out.println("Oh My!");
        } catch (Exception igno<caret>re) {
        } catch (Exception ign<caret>ored) {
        } catch (Exception x) {
        }
    }

    private State computeSmthg() {
        return new State();
    }

    private int computeAnswerForTheUltimateQuestionOfLife() {
        return 42;
    }

    private int getSomeSchoolNumber() {
        return 239;
    }

    public MyComponent returnMy() {
        return new MyComponent();
    }

    class State();
    class MyComponent();
}

class Foo2 extends Foo {
    @java.lang.Override
    public void boo(int <caret>boi){
        System.out.println("Hello World!");
    }
}
