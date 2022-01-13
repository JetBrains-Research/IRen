package testData.excludeFromInspection;

public class Foo {
    public void boo(int boi) {
        try {
            System.out.println("Oh My!");
        } catch (Exception igno<caret>re) {
        } catch (Exception ign<caret>ored) {
        } catch (Exception e) {
        }
    }
}

class Foo2 extends Foo {
    @java.lang.Override
    public void boo(int bo<caret>i){
        System.out.println("Hello World!");
    }
}
