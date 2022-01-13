package testData.parsing.code;

public class MyClass {
    public int myField = 0;

    public void setField(int field) {
        this.myF<caret>ield = field;
    }

    public static void main(String[] args) {
        MyClass instance = new MyClass();
        instance.myField = 1;
    }
}
