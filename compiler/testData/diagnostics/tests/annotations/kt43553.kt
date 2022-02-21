annotation class Anno<T>

fun func(@Anno<String> param: String): String = param

fun box(): String {
    func<!WRONG_NUMBER_OF_TYPE_ARGUMENTS!><String><!>("")
    return ""
}