package collision

class MyClass(not_collision: Boolean, val collision1: Int = 0) {
    var myField = 0
    private val collision2 = 1
    fun setField(boi: Int) {
        myField = boi
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val instance = MyClass(true)
            instance.myFie<caret>ld = 1
        }
    }
}