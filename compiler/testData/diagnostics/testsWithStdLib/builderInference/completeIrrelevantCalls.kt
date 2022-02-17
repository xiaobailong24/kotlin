// FIR_IDENTICAL
// SKIP_TXT

class A {
    lateinit var m: Map<String, Int>

    @ExperimentalStdlibApi
    fun foo(xs: Collection<List<String>>) {
        m = buildMap {
            // flatMap calls might be completed on early phase
            for (x in xs.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>flatMap { it.toList() }<!>) {
                put(x, x.length)
            }
        }
    }
}
