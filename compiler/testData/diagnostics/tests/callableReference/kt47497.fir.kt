fun <T> bar(x: T): T = x

fun nullableFun(): ((Int) -> Int)? = null

fun box(): String {
    val x2: (Int) -> Int = bar(nullableFun() ?: ::Boolean)
    return "OK"
}