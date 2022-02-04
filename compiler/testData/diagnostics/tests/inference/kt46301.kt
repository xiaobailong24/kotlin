sealed interface A
sealed interface B

data class X(val something: String): A, B
data class Y(val something: String): A, B

fun ok(a: A): B {
    return when (a) {
        is X -> <!DEBUG_INFO_SMARTCAST!>a<!>
        is Y -> <!DEBUG_INFO_SMARTCAST!>a<!>
    }
} // compiles

fun problem(a: A): B {
    return when (a) {
        is X, is Y -> <!TYPE_MISMATCH!>a<!>
    }
} // nope :(