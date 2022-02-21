// FIR_IDENTICAL
fun foo(a: Int) {}
fun foo(a: Long) {}

fun bar() = ::foo // compile error expected: ambiguous foo resolution

fun main() {
    val d = bar()
}