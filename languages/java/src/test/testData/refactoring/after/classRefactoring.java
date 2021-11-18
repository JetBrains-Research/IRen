public class newName {
    int myField = 0;

    public void setField(int field) {
        this.myField = field;
    }

    public static void main(String[] args) {
        newName clz = new newName();
        clz.setField(1);
    }
}
