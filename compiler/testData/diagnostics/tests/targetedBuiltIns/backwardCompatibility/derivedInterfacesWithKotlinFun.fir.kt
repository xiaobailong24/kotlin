fun foo(x: Int) = 1

fun bar(x: (Int) -> Int) {}

fun main() {
    val x = bar(::foo)
}