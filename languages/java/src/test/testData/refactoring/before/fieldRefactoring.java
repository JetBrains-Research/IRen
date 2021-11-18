public class MyClass {
    int myField = 0;

    public void setField(int field) {
        this.myF<caret>ield = field;
    }

    public static void main(String[] args) {
        MyClass clz = new MyClass();
        clz.newName(1);
    }
}
