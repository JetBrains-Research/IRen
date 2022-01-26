package testData.excludeFromInspection;

public class Fooo {
    public void boo(int b<caret>oi) {
        try {
            System.out.println("Oh My!");
        } catch (Exception ignore) {
        } catch (Exception ignored) {
        } catch (Exception <caret>x) {
        }
    }
}

class Fooo2 extends Fooo {
    @java.lang.Override
    public void boo(int boi){
        System.out.println("Hello World!");
    }
}
