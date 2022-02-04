// WITH_STDLIB
// FULL_JDK

import java.util.*

fun compiles() {
    val map: AbstractMap<String, String> = TreeMap()
    map.set("key", null) // No error. non-null not enforced (platform type)
}

fun `does not compile`() {
    val map: TreeMap<String, String> = TreeMap()
    map.set("key", <!NULL_FOR_NONNULL_TYPE!>null<!>) // Error: NULL_FOR_NONNULL_TYPE. Why? Isn't this also a platform type?
}