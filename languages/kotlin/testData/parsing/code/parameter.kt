package parsing.code

class Parameter {
    fun foo(arg<caret>s: Array<String>) {
        if (args.isNotEmpty()) {
            val s = args[0]
        }
    }
}
