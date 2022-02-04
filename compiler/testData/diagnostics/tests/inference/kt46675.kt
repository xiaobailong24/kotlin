interface I<T>

fun <T> foo(bar: Iterable<I<in T>>): I<T> = TODO()

/*
 T == U
 List<out I<out K>> <: Iterable<out I<in T>>
 I<out K> <: I<in T>
 CapturedType(out K) <: CapturedType(in T)
 */
fun <K, U : K> I<K>.baz(that: I<U>) = foo<U>(listOf(this, that))
