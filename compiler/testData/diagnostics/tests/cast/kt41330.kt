// FIR_IDENTICAL
fun foo1(a: Any) {
    a as Int
    a as String // no warning
}

fun foo2(a: Any) {
    a as? Int // no warning
    a is Int // no warning
    a !is Int // no warning
}