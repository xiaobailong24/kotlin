abstract class Base() {
    init {
        foo()
    }
    abstract fun foo()
}

class C(val some: Int)

class Derived(val x: C) : Base() {
    override fun foo() {
        x?.some
    }
}

fun box(): String {
    Derived(C(42))
    return "OK"
}