package parsing.code

class MyClass {
    var myField = 0
    val myText = """Parsing test!!! Number: $myField
                    ¯\_(ツ)_/¯"""

    fun setField(field: Int) {
        this.myF<caret>ield = field
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val instance = MyClass()
            instance.myField = 1
        }
    }
}