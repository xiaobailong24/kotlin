// FIR_IDENTICAL
class C(val value: String) {
    fun foo() {}
}

fun bar(x: Any) {
    throw Exception()
}

fun foo() {
    var r: Any = ""
    r = C("OK")
    try {                       // r :: C
        r = r.value             // r :: String
        bar(r)                  // throws exception
        r = C("")
    } catch (e: Exception) {
        r.foo()                 // r smart cast to C
    }
}

fun main() {
    foo()
}