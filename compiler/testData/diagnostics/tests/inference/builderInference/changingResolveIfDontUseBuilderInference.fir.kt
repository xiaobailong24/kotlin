// WITH_RUNTIME
// !DIAGNOSTICS: -EXPERIMENTAL_IS_NOT_ENABLED
// DONT_TARGET_EXACT_BACKEND: WASM

import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
fun <K, V> buildMap(@BuilderInference builderAction: MutableMap<K, V>.() -> Unit): Map<K, V> = mapOf()

fun foo(): MutableMap<CharSequence, *> = mutableMapOf<CharSequence, String>()

fun <E> MutableMap<E, *>.swap(x: MutableMap<E, *>) {}

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val x: Map<in String, String> = buildMap {
        put("", "")
        swap(foo())
    } // `Map<CharSequence, String>` if we use builder inference, `Map<String, String>` if we don't
}