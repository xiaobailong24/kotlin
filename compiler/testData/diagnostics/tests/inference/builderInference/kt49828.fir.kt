// WITH_STDLIB

fun main() {
    val list = buildList {
        add("one")
        add("two")

        val secondParameter = get(1)
        println(secondParameter) // ERROR: [OVERLOAD_RESOLUTION_AMBIGUITY] Overload resolution ambiguity. All these functions match.
    }
}