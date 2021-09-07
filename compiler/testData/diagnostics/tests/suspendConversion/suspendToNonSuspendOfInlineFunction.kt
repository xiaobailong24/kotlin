inline fun go1(crossinline f: () -> String) = f()
<!NOTHING_TO_INLINE!>inline<!> fun go2(noinline f: () -> String) = f()
fun go3(f: () -> String) = f()

fun builder(c: suspend () -> Unit) {}

suspend fun String.id(): String = this

fun box() {
    val x = ""
    val y: suspend () -> String = x::id
    builder {
        val res1 = go1 { x.<!NON_LOCAL_SUSPENSION_POINT!>id<!>() }
        val res2 = go1(<!TYPE_MISMATCH("() -> String; KSuspendFunction0<String>")!>x::id<!>)
        val res3 = go1(<!TYPE_MISMATCH("() -> String; suspend () -> String")!>y<!>)
        val res4 = go2 { x.<!NON_LOCAL_SUSPENSION_POINT!>id<!>() }
        val res5 = go2(<!TYPE_MISMATCH("() -> String; KSuspendFunction0<String>")!>x::id<!>)
        val res6 = go2(<!TYPE_MISMATCH("() -> String; suspend () -> String")!>y<!>)
        val res7 = go3 { x.<!NON_LOCAL_SUSPENSION_POINT!>id<!>() }
        val res8 = go3(<!TYPE_MISMATCH("() -> String; KSuspendFunction0<String>")!>x::id<!>)
        val res9 = go3(<!TYPE_MISMATCH("() -> String; suspend () -> String")!>y<!>)
    }
}
