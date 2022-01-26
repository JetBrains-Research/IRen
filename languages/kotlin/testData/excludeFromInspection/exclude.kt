package excludeFromInspection

open class Foo {
    open var xy = 0
    open fun boo(boi: Int) {
        val st<caret>ate = computeSmth()
        val compo<caret>nent = returnMy()
        val answerForTheUltimateQu<caret>estionOfLife = computeAnswerForTheUltimateQuestionOfLife()
        try {
            println("Oh My!")
        } catch (ign<caret>ore: Exception) {
        } catch (ig<caret>nored: Exception) {
        } catch (<caret>_: Exception) {
        } catch (e: Exception) {
            println("Exception is not ignored")
        }
    }

    private fun computeSmth(): State {
        return State()
    }

    private fun returnMy(): MyComponent {
        return MyComponent()
    }

    private fun computeAnswerForTheUltimateQuestionOfLife(): Int {
        return 42
    }

    class State
    class MyComponent
}

internal class Foo2 : Foo() {
    override var x<caret>y = 1
    override fun boo(b<caret>oi: Int) = println("Hello World!")
}
