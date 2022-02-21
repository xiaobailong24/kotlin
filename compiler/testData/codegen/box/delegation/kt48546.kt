// IGNORE_BACKEND: JVM_IR
// JVM IR status: throwing exception because of FE, see KT-48546
// WITH_STDLIB
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: allowing this code, runtime StackOverflowError

object DelegateTest {
    var result = ""
    val f by lazy {
        // println(f) // TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM
        result += f.toString() // Compiler crash
        "hello"
    }
}

fun box(): String {
    return if (DelegateTest.f == "hello") "OK" else "NOK"
}
