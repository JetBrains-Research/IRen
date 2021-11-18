public class MyClass {
    int myField = 0;

    public void newName(int field) {
        this.myField = field;
    }

    public static void main(String[] args) {
        MyClass clz = new MyClass();
        clz.newName(1);
    }
}
