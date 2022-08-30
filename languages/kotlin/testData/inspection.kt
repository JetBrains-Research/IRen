// carets define highlighted elements
package org.jetbrains.iren;

open class Foo {
    open var x<caret>y = 0
    open fun boo(b<caret>oi: Int) {
        val state = computeSmth()
        val component = returnMy()
        val answerForTheUltimateQuestionOfLife = computeAnswerForTheUltimateQuestionOfLife()
        val school = getSomeSchoolNumber()
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

    private fun computeSmth(): State {
        return State()
    }

    private fun returnMy(): MyComponent {
        return MyComponent()
    }

    private fun computeAnswerForTheUltimateQuestionOfLife(): Int {
        return 42
    }

    private fun getSomeSchoolNumber(): Int {
        return 239
    }

    class State
    class MyComponent
}

internal class Foo2 : Foo() {
    override var xy = 1
    override fun boo(boi: Int) = println("Hello World!")
}
