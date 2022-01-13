package testData.variable;

public class MyClas<caret>s {
    public int myField = 0;
    public static int collision = 1;

    public void setF<caret>ield(int boi) {
        myField = boi;
    }

    public static void main(String[] args) {
        MyClass instance = new MyClass();
        instance.myField = 1;
    }
}
