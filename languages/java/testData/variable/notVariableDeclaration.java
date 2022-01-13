package testData.variable;

public class MyClas<caret>s {
    public int myField = 0;
    public static int collision = 1;

    public void setF<caret>ield(int boi) {
        myFi<caret>eld = bo<caret>i;
    }

    public stat<caret>ic void main(String[] args) {
        MyClass instance = new MyClass();
        insta<caret>nce.myField = 1;
    <caret>}
}
