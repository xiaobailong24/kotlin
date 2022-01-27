class Bar<K> {
    inner class Inner
}

class Foo<in T> {
    fun yuckyEventHandler(
        // should be type variance conflict error on `T`
        // reported by FIR, but skipped in FE 1.0
        fn: Bar<T>.Inner.() -> Unit): () -> Unit = TODO()
}