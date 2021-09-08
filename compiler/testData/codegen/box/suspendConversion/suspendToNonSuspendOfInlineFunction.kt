// WITH_RUNTIME
// !LANGUAGE: +SuspendConversion
// IGNORE_BACKEND: JVM
// TARGET_BACKEND: JVM

import kotlin.coroutines.*

inline fun go(f: () -> String) = f()

suspend fun String.id(): String = this

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(Continuation(EmptyCoroutineContext){
        it.getOrThrow()
    })
}

fun box(): String {
    builder {
        val x = ""
        val y: suspend () -> String = x::id
        val res1 = go { x.id() }
        val res2 = go(x::id) // if we pass to BE `x::id` as non-suspend, BE fails
        @Suppress("TYPE_MISMATCH")
        val res3 = go(y)
    }
    return "OK"
}