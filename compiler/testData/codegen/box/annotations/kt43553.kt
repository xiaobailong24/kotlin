annotation class Anno<T>

fun func(@Anno<String> param: String): String = param

fun box(): String {
    func<String>("")
    return ""
}