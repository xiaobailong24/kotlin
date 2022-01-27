// WITH_STDLIB

fun test(x: Function<Any>): Unit {}

fun main() {
    val x: Function<Any> = (
            if (true) {x: String, y: String -> x + y}
            else fun(x: String) = x
            ) // this works
    test(
        if (true) { <!NAME_SHADOWING!>x<!>: String, y: String -> x + y}
        else <!TYPE_MISMATCH, TYPE_MISMATCH!>fun<!EXPECTED_PARAMETERS_NUMBER_MISMATCH!>(x: String)<!> = x<!>
    ) // this does not work
}