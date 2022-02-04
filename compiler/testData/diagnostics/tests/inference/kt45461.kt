// FIR_IDENTICAL
class Foo<T>

class Bar<T> {
    fun <S : T> takeFoo(foo: Foo<in S>) {}
}

fun main() {
    val foo = Foo<Int>()
    Bar<String>().takeFoo(foo) // error in 1.3.72, no error in 1.4.31
}