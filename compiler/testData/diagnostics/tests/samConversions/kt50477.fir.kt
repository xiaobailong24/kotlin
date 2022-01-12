fun interface FI {
    suspend fun call() // suspending now(!!!)
}

fun accept(fi: FI): Int = TODO()

fun main() {
    val fi: suspend () -> Unit = {} // Lambda of a suspending(!!!) functional type
    accept(fi) // ERROR: Type mismatch. Required: FI Found: suspend () → Unit
}