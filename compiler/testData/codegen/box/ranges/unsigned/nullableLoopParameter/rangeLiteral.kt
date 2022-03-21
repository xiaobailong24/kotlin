// IGNORE_BACKEND: JVM
// See KT-38833: Runtime exception is "java.lang.ClassCastException: java.lang.Integer cannot be cast to kotlin.UInt"
// IGNORE_LIGHT_ANALYSIS
// WITH_STDLIB

abstract class CLASSS {
    abstract fun foo()
}

class DERIVED : CLASSS() {
    override fun foo() = Unit
}

fun frrrrr(c: CLASSS) {
    c.foo()
}

fun box(): String {
    frrrrr(DERIVED())
    return "OK"
}