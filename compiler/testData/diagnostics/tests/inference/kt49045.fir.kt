data class Foo(val foo: String)

class Bar(val bar: String)

fun Bar.toFoo() = Foo(bar)

class Wrapper<T>(val wrapped: T) {
    fun <A> map(f: (T) -> A): Wrapper<A> = Wrapper(f(wrapped))
    fun swapWrappedValue(f: (T) -> Wrapper<T>): Wrapper<T> = f(wrapped)
}

fun getFoo(): Wrapper<Foo?> {
    return Wrapper(Bar("bar"))
        // Useless cast reported below on "as Foo?". However this is required otherwise the code won't compile as the
        // inferred type is Wrapper<Foo>, so the next line, which tries to put null into the wrapped value, is invalid.
        .map { it.toFoo() as Foo? } // [USELESS_CAST] No cast needed
        .swapWrappedValue { Wrapper(null) }
}