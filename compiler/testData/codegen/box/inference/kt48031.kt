// WITH_STDLIB

class Flow<out T>(val x: T)

@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
fun <T> Flow<T>.debounce(timeoutMillis: (T) -> Long): Flow<T> = this

@JvmName("debounceDuration")
@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
fun <T> Flow<T>.debounce(timeout: (T) -> String): Flow<T> = this

fun invalidFlow(x: Flow<Int>): Flow<Int> = x.debounce { value -> 0 }

fun box(): String = if (invalidFlow(Flow(1)).x == 1) "OK" else "NOK"
