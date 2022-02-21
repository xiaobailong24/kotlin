// WITH_STDLIB

sealed class PageEvent2<T : Any> {
    class Insert<T : Any> constructor(val pages: T) : PageEvent2<T>()
    class LoadStateUpdate<T : Any>() : PageEvent2<T>()
}

fun main() {

    <!TYPE_MISMATCH!>listOf<!>(
        <!TYPE_MISMATCH!>listOf<!>(
            <!TYPE_MISMATCH!>PageEvent2.LoadStateUpdate()<!>,
            PageEvent2.Insert(4),
            <!TYPE_MISMATCH!>PageEvent2.LoadStateUpdate()<!>,
        )
    )
}