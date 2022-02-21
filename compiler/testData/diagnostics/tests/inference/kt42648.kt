// WITH_STDLIB

fun main() {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>takeLambda<!> { 1 }
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>takeLambda<!> { "" }
}

@JvmName("takeLambdaNullable")
fun takeLambda(block: () -> String) {
    TODO()
}

fun takeLambda(block: () -> Int) {
    TODO()
}
