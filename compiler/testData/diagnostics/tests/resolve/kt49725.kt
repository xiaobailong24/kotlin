interface A {
    fun Any.equals(other: Any?): Boolean = false
}

fun test(a: A) {
    println(a == a)
}