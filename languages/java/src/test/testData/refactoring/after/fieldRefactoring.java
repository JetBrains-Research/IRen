public class MyClass {
    int newName = 0;

    public void setField(int field) {
        this.newName = field;
    }

    public static void main(String[] args) {
        MyClass clz = new MyClass();
        clz.newName(1);
    }
}
