// FIR_IDENTICAL
class Foo1
class Foo2<R>

fun Foo1.bar(fn: () -> Unit): () -> Unit = TODO()
fun <E> Foo1.bar(fn: (E) -> Unit): () -> Unit = TODO()

fun <R> Foo2<R>.bar(fn: () -> Unit): () -> Unit = TODO()
fun <E, R> Foo2<R>.bar(fn: (E) -> Unit): () -> Unit = TODO()

fun main(ctx1: Foo1, ctx2: Foo2<String>) {
    val x: () -> Unit = ctx1.bar {  }
    val y: () -> Unit = ctx2.<!OVERLOAD_RESOLUTION_AMBIGUITY!>bar<!> {  }
}
