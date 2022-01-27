// WITH_STDLIB

fun <T> take(element: T) {}

val operations = take<Long.() -> Unit>(
    { this + 2 }.id() // error
)

fun <T> T.id(): T = this
