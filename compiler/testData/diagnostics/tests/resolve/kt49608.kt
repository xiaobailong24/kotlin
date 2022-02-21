// WITH_STDLIB

interface Lens<P, T> {
    fun get(p: P): T
    fun set(p: P, v: T): P
}

fun <P, T, X> Lens<P, List<T>>.setEach(parent: P, path: Lens<T, X>, value: X): P =
    this.set(parent, this.get(parent).map { path.set(it, value) })

fun <P, T, X> Lens<P, List<T>>.setEach(parent: P, path: Lens<T, X>, value: (T) -> X): P =
    this.set(parent, this.get(parent).map { path.set(it, value(it)) })

data class One(val list: List<Two>)
data class Two(val string: String)

object OverloadTest {
    object OneTwo : Lens<One, List<Two>> {
        override fun get(p: One): List<Two> = p.list
        override fun set(p: One, v: List<Two>): One = p.copy(list = v)
    }

    object TwoString : Lens<Two, String> {
        override fun get(p: Two): String = p.string
        override fun set(p: Two, v: String): Two = p.copy(string = v)
    }

    fun test(p: One) = OneTwo.setEach(p, TwoString, "A")
    fun testB(p: One) = OneTwo.<!OVERLOAD_RESOLUTION_AMBIGUITY!>setEach<!>(p, TwoString) { "A" } // this runs into the error

    val func: (Two) -> String = { "A" }
    fun testC(p: One) = OneTwo.setEach(p, TwoString, func) // this strangely not
}