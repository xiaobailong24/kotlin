class A<T>(
    val value: T
)

class B<T>(
    val value: T,
    val a: A<T>
)

fun Any.copy(): Any? = when(this) {
    is B<*> -> B(value, a) // Type mismatch: inferred type is Any? but CapturedType(out Any?) was expected
    else -> null
}