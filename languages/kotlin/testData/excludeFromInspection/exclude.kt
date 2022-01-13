package excludeFromInspection

open class Foo {
    open var xy = 0
    open fun boo(boi: Int) {
        try {
            println("Oh My!")
        } catch (ign<caret>ore: Exception) {
        } catch (ig<caret>nored: Exception) {
        } catch (<caret>_: Exception) {
        } catch (e: Exception) {
            println("Exception is not ignored")
        }
    }
}

internal class Foo2 : Foo() {
    override var x<caret>y = 1
    override fun boo(b<caret>oi: Int) = println("Hello World!")
}
