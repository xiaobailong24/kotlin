class A<K>
fun <K> A<K>.foo(k: K) = k
fun <K> A<K>.foo(a: A<K>.() -> Unit) = 2
fun test(){
    A<Int>().<!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!> {}  // ambiguous overload, should not be ambiguous

    // Overload resolution is correct if type params given
    A<Int>().foo<Int> {} // works
    A<Int>().<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>foo<!><Any> {} // fails which is correct
}