// WITH_STDLIB

import kotlin.experimental.ExperimentalTypeInference
import kotlin.time.Duration

sealed class Foo {
    object A: Foo()
    data class B(val a: Int): Foo()
}

interface Flow<out T>

fun <T> flowOf(vararg elements: T): Flow<T> = TODO()

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
fun <T> Flow<T>.debounce(timeoutMillis: (T) -> Long): Flow<T> = TODO()

@JvmName("debounce2")
fun <T> Flow<T>.debounce(timeout: (T) -> Duration): Flow<T> = TODO()

fun invalidFlow(): Flow<Foo> {
    return flowOf(Foo.A, Foo.B(1)).debounce { value -> if (value == Foo.A) 0 else 1000L }
}