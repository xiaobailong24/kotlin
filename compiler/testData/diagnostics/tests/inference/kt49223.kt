// FIR_IDENTICAL
// WITH_STDLIB

fun main() {
    val strList = mutableListOf("a")
    val intList = mutableListOf(1)
    val lists: List<MutableList<*>> = listOf(strList, intList)
    // Removing `map { it }` makes the compiler correctly reject this, apparently the `map` operation has the effect of silently changing the scope of capturing.
    /// That is, the input type to `map`, `List<MutableList<*>>`, should be transformed to a `List<MutableList<captured out Any>>` or something like `List<MutableList<Σ T. T>>`, while the output of `map` seems to be something like `List<Σ T. MutableList<T>>` in order for `merge` to type check.
    // BTW, both type arguments of `map` are instantiated to `List<MutableList<*>>` in the FIR tree after resolution, which is not precise but seems correct.
    merge(lists)
    val s = strList[1] // boom!
}

fun <T> merge(lists: List<MutableList<T>>) = lists[0].addAll(lists[1])
