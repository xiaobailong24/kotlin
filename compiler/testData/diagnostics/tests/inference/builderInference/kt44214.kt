interface I

@ExperimentalStdlibApi
fun foo(): List<I> {
    return buildList {
        fun addBar() {
            this += bar()
        }
        addBar()
    }
}

fun bar(): I? = null