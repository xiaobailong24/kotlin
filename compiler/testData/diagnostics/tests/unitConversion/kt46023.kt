interface Builder<Y> {
    fun get(): Y
}

fun onValidate(init: () -> Unit) {
}

fun <T> verify(init: () -> Builder<T>): T {
    TODO()
}

fun builder(): Builder<String> {
    TODO()
}

fun main() {
    onValidate {
        val result = verify { builder() }
        result // OK
    }

    onValidate {
        verify { <!TYPE_MISMATCH, TYPE_MISMATCH, TYPE_MISMATCH!>builder()<!> } // error
    }
}
