inline fun go(f: () -> String) = f()

suspend fun String.id(): String = this

suspend fun box() {
    val x = ""
    val y: suspend () -> String = x::id
//    val res1 = go { x.id() }
    val res2 = go(x::id) // if we pass to BE `x::id` as non-suspend, BE fails
//    val res3 = go(y)
}