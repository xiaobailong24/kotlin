// FIR_IDENTICAL
class Scope {
    fun scopeFun() {}
}

open class A {
    open val f: Scope.() -> Unit = {
        scopeFun()
    }
}

class B : A() {
    override val f: Scope.() -> Unit = {
        //Does not work
        super.f(<!NO_VALUE_FOR_PARAMETER!>)<!> // [NO_VALUE_FOR_PARAMETER] No value passed for parameter 'p1'
        //Does work
        val x = super.f
        x()
    }
}