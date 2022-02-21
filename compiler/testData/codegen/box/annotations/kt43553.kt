// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: not supported yet, see KT-48444
// JVM IR status: throwing exception because of FE, see KT-43553

@Target(AnnotationTarget.TYPE)
annotation class Qualifier<T>

fun <T> func(param: String): @Qualifier<T> String = param

fun box(): String {
    func<String>("")
    return "OK"
}
