// WITH_STDLIB

interface R
open class A : R
class B : A()

abstract class RootScope

open class AScope : RootScope() {
    @JvmName("doStuffA")
    fun doStuff(action: (A) -> Unit) = "OK"
}
fun a(configure: AScope.() -> Unit) = configure(AScope())

class BScope : AScope() {
    @JvmName("doStuffB")
    fun doStuff(action: (B) -> Unit) = "NOK"
}
fun b(configure: BScope.() -> String): String = configure(BScope())

fun box(): String {
    var r: R? = null

    return b {
        doStuff { r = it }
    }
}
