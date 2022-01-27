// WITH_STDLIB

object DelegateTest {
    var result = ""
    val f by lazy {
        // println(f) // TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM
        result += f.toString() // Compiler crash
        "hello"
    }
}