// KT-46643
// IGNORE_BACKEND: WASM

external abstract class Base {
    open val foo: String
}

open class A : Base() {
    override var foo: String = "Error: A setter was not called."
        set(k) { result = "O$k"}

    lateinit var result: String
}

open class B : Base() {
    override val foo: String = "OK"

    open val result: String get() = foo
}

class C : B() {
    override var foo: String = "Error: C setter was not called."
        set(k) { result = "O$k"}

    override lateinit var result: String
}

open class D : B() {
    override val foo: String = "OK"
}

open class E : D() {
    override var foo: String = "Error: E setter was not called."
        set(k) { result = "O$k"}

    override lateinit var result: String
}

fun box(): String {
    val a = A()
    if (a.result != "OK") return a.foo

    val b = B()
    if (b.result != "OK") return b.foo

    val c = C()
    if (c.result != "OK") return c.foo

    val d = D()
    if (d.result != "OK") return d.foo

    val e = E()
    if (e.result != "OK") return e.foo

    return "OK"
}