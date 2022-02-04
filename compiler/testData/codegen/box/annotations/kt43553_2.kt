@Target(AnnotationTarget.TYPE)
annotation class Qualifier<T>

fun <T> func(param: String): @Qualifier<T> String = param

fun box(): String {
    func<String>("")
    return "OK"
}
