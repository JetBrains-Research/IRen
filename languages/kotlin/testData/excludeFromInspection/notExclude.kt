package excludeFromInspection

open class Foo3 {
    open var xy = 0
    open fun boo(b<caret>oi: Int) {
        try {
            println("Oh My!")
        } catch (ignore: Exception) {
        } catch (ignored: Exception) {
        } catch (_: Exception) {
        } catch (<caret>e: Exception) {
            println("Exception is not ignored")
        }
    }
}

internal class Foo4 : Foo3() {
    override var xy = 1
    override fun boo(boi: Int) = println("Hello World!")
}
