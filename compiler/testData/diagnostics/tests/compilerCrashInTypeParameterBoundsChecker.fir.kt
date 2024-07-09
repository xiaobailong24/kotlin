// SUPPRESS_NO_TYPE_ALIAS_EXPANSION_MODE: TBD
// ISSUE: KT-64644
// WITH_STDLIB

typealias MaybePair = Pair<Int, Int>?

fun <T: MaybePair> foo(x: T) {
    if (x != null) {
        println(x.first)
        println(x.second)
    }
}
