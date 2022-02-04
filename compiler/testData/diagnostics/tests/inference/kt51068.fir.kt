// WITH_STDLIB

fun main() {
    listOf<suspend (Int) -> Int>(<!ARGUMENT_TYPE_MISMATCH!>{ x: Int -> x * x }<!>) //error
}