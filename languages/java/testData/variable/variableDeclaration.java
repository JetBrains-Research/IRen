package testData.variable;

public class MyClass {
    public int myFi<caret>eld = 0;
    public static int colli<caret>sion = 1;

    public void setField(int b<caret>oi) {
        myField = boi;
    }

    public static void main(String[] arg<caret>s) {
        MyClass insta<caret>nce = new MyClass();
        instance.myField = 1;
    }
}