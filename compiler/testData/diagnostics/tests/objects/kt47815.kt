interface A : Test

open class Test {
    fun <T> result() = object : A { }
}