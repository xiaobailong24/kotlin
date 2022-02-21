// WITH_STDLIB

abstract class Base<T> {
    final var foo: MutableList<T>? = mutableListOf()
}

class Bar : Base<String>()

fun main() {
    val bar = Bar()
    bar.foo = mutableListOf() // works
    bar.foo = null // works
}

fun test(baz: Any) {
    if (baz is Base<*>) {
        baz.foo = <!ASSIGNMENT_TYPE_MISMATCH!>mutableListOf()<!> // should not work and doesn't
        baz.foo = null // should work but doesn't, error: "Setter for 'foo' is removed by type projection"
    }
}
