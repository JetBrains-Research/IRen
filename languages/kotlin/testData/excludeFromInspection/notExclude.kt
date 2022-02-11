package excludeFromInspection

open class Foo3 {
    open var xy = 0
    open fun boo(b<caret>oi: Int) {
        val sc<caret>h = getSomeSchoolNumber()
        try {
            println("Oh My!")
        } catch (ignore: Exception) {
        } catch (ignored: Exception) {
        } catch (_: Exception) {
        } catch (<caret>e: Exception) {
            println("Exception is not ignored")
        }
    }

    private fun getSomeSchoolNumber(): Int {
        return 239
    }
}

internal class Foo4 : Foo3() {
    override var xy = 1
    override fun boo(boi: Int) = println("Hello World!")
}
