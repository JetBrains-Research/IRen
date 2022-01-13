package variable

class MyClass<caret>2(not_collision: Boolean, val collision1: Int = 0) {
    var myField = 0
    private val collision2 = 1
    fun setFi<caret>eld(boi: Int) {
        myField = boi
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val instance = MyClass2(true)
            instance.myField = 1
        }
    }
}
