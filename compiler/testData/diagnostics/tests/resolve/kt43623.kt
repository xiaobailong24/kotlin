// FIR_IDENTICAL
// WITH_STDLIB

class A<K>{ fun bar() = 1 }
fun <K> A<K>.foo(k: Pair<String, K>) = k
fun <K> A<K>.foo(a: Pair<String, A<K>.() -> Unit>) = 2


fun main() {
    A<Int>().foo("" to 1) 			// ok
    A<Int>().<!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>("" to { <!UNRESOLVED_REFERENCE!>bar<!>() })   // fails to infer correct overload
}