annotation class Anno<T>

fun func(@<!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>Anno<!><String> param: String): String = param

fun box(): String {
    <!INAPPLICABLE_CANDIDATE!>func<!><String>("")
    return ""
}