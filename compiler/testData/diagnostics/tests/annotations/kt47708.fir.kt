// WITH_STDLIB

internal fun interface StableInterface {
    @ExperimentalStdlibApi
    fun experimentalMethod()
}

fun regressionTestOverrides() {
    val anonymous: StableInterface = object : StableInterface {
        override fun <!OPT_IN_OVERRIDE_ERROR!>experimentalMethod<!>() {} // correctly fails check
    }
    val lambda = StableInterface {} // this does not get flagged
}