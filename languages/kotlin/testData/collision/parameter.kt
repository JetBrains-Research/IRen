package collision

class Foo {
    var boi: Int = 0
    fun main(arg<caret>s: Array<String>, collision: Int) {
        boi = args.size
        if (args.isNotEmpty()) {
            val i = args[0]
        }
    }
}
