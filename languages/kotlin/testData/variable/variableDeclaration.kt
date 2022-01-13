package variable

class MyClass4(not_colli<caret>sion: Boolean, val collis<caret>ion1: Int = 0) {
    var myFie<caret>ld = 0
    private val collis<caret>ion2 = 1
    fun setField(bo<caret>i: Int) {
        myField = boi
    }

    companion object {
        @JvmStatic
        fun main(ar<caret>gs: Array<String>) {
            val inst<caret>ance = MyClass4(true)
            instance.myField = 1
        }
    }
}