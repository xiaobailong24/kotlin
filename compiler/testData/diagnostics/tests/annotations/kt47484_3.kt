// WITH_STDLIB

import kotlin.properties.*
import kotlin.reflect.*
import kotlin.math.*
interface I {
    fun <T : String> f(x: T?) = x ?: "OK".strip()?.substringBeforeLast('', C().f<Long>(-62))!!
}

class C : I

fun box() = C().f<String>(null)