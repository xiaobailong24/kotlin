// FIR_IDENTICAL
open class Base<S>
class BaseInt: Base<Int>()

class A<S, T: Base<S>> {
    fun f(x: T) {}
}

fun <S, T: Base<S>> createInstance(value: S): T { TODO() }

fun main() {
    val a = A<Int, BaseInt>()

    val x = a.f(createInstance(10)) // compiled as expected
    val y = a.f(createInstance("1")) // also compiled, although it should not
}
