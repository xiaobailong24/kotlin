inline fun go1(crossinline f: () -> String) = f()
inline fun go2(noinline f: () -> String) = f()
fun go3(f: () -> String) = f()

fun builder(c: suspend () -> Unit) {}

suspend fun String.id(): String = this

fun box() {
    val x = ""
    val y: () -> String = { "" }
    builder {
        val res1 = go1 { x.id() }
        val res2 = go1(x::id)
        val res3 = go1(y)
        val res4 = go2 { x.id() }
        val res5 = go2(x::id)
        val res6 = go2(y)
        val res7 = go3 { x.id() }
        val res8 = go3(x::id)
        val res9 = go3(y)
    }
}