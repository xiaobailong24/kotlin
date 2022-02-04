fun (suspend () -> Unit).extensionFunc() {}
fun parameterFunc(func: suspend () -> Unit) {}
fun testFunc() {}

fun main()
{
    parameterFunc(::testFunc)
    (::testFunc).<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>extensionFunc<!>() // unresolved reference
}