fun <T> bar(x: T): T = x

fun nullableFun(): ((Int) -> Int)? = null

fun box(): String {
    val x2: (Int) -> Int = <!TYPE_MISMATCH, TYPE_MISMATCH!>bar(nullableFun() ?: <!INVISIBLE_MEMBER!>::Boolean<!>)<!>
    return "OK"
}
