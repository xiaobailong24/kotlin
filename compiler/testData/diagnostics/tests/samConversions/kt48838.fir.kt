fun interface FI {
    fun foo(i: Int): Int
}

fun test1(x: FI) {}

fun <T> test2(x: T) {}

fun main() {
    test1({x: Int -> x})
    test2<FI>(<!ARGUMENT_TYPE_MISMATCH!>{x: Int -> x}<!>) // error [TYPE_MISMATCH] Type mismatch.
}