package testData.collision;

public class MyClass {
    public int myField = 0;
    public static int collision = 1;

    public void setField(int boi) {
        myField = boi;
    }

    public static void main(String[] args) {
        MyClass instance = new MyClass();
        instance.myFie<caret>ld = 1;
    }
}
