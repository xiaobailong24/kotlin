class A<T>

fun <T> A<T>.doSomething(v: T) = Unit
fun <T> A<T>.doSomething(v: (T) -> T) = Unit

fun main() {

    //Works fine
    A<String>().doSomething("")

    //Cannot choose among the candidates without completing type inference
    A<String>().doSomething { "" }
}