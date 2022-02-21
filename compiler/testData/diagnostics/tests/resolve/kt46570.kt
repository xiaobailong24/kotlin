open class A<X>
class B<X, Y> : A<Y>()

fun <X> foo(a: A<X>, stub: A<X>.() -> Unit) = "A"
fun <X, Y> foo(a: B<X, Y>, stub: B<X, Y>.() -> Unit) = "B"

fun main() {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>(B<Any, Any>()) { /* compile error: overload ambiguity */ }
}