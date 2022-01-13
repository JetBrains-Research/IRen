package variable

class MyClass3(not_collision: Boolean, val collision1: Int = 0) {
    var myField = 0
    private val collision2 = 1
    fun setField(boi: Int) {
        myFi<caret>eld = b<caret>oi
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val instance = MyClass3(true)
            in<caret>stance.my<caret>Field = 1
        }
    }
}