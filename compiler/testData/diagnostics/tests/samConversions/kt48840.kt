fun interface FI {
    fun foo(p: Double): Long
}

fun bar(fi: FI) {}

class A {
    fun buz(p: Double): Long {
        return p.toLong()
    }
}

fun foo() {
    bar({p: Double -> p.toLong()}) // works
    val v1: FI = FI {p: Double -> p.toLong()} // works

    val a: A = A()
    bar(a::buz) // Works
    val v3: FI = <!TYPE_MISMATCH!>a::<!TYPE_MISMATCH!>buz<!><!> // Fails
    val v4: FI = a::buz as FI // Work-around
}