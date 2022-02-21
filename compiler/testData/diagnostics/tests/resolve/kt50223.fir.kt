abstract class MyClass {
    abstract fun <P1> foo(): (P1) -> Unknown<String>

    private fun callTryConvertConstant() {
        println(foo<String>())
    }
}