// WITH_STDLIB

fun main() {
    listOf<suspend (Int) -> Int>(<!TYPE_MISMATCH!>{ x: Int -> <!TYPE_MISMATCH!>x * x<!> }<!>) //error
}