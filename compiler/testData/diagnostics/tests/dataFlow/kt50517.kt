// !DIAGNOSTICS: +UNUSED_PARAMETER
// WITH_EXTENDED_CHECKERS

fun main() = Unit

operator fun String.invoke() {
}

// PROBLEM: 'UNUSED_PARAMETER' warning for headingLine
class TheProblem1 {
    fun <T: Any> appendList(<!UNUSED_PARAMETER!>headingLine<!>: String) {
        "$headingLine:"()
    }

    operator fun String.invoke() {
    }

    companion object
}

// PROBLEM: Also for inner class
class TheProblem2 {
    inner class Inner {
        fun <T: Any> appendList(<!UNUSED_PARAMETER!>headingLine<!>: String) {
            "$headingLine:"()
        }
    }

    operator fun String.invoke() {
    }

    companion object
}

// PROBLEM: Also for nested class of object
object TheProblem3 {
    class Inner {
        fun <T: Any> appendList(<!UNUSED_PARAMETER!>headingLine<!>: String) {
            "$headingLine:"()
        }
    }

    operator fun String.invoke() {
    }
}

// PROBLEM: Also for extension function in companion class
class TheProblem4 {
    inner class Inner {
        fun <T: Any> appendList(<!UNUSED_PARAMETER!>headingLine<!>: String) {
            "$headingLine:"()
        }
    }

    companion object {
        operator fun String.invoke() {
        }
    }
}

// CORRECT: when we pass as the parameter, no warnings
class Correct1 {
    fun <T: Any> appendList(headingLine: String) {
        invoke("$headingLine:")
    }

    fun invoke(@Suppress("UNUSED_PARAMETER") param: String) {
    }

    companion object
}

// CORRECT: when we call the function in a file, no wranings
class Correct2 {
    fun <T: Any> appendList(headingLine: String) {
        "$headingLine:"()
    }

    companion object
}

// CORRECT: when the class don't have companion object, no warnings
class Correct3 {
    fun <T: Any> appendList(headingLine: String) {
        "$headingLine:"()
    }

    operator fun String.invoke() {
    }
}

// CORRECT: when we use without template literal, no warnings
class Correct4 {
    fun <T: Any> appendList(headingLine: String) {
        headingLine()
    }

    operator fun String.invoke() {
    }

    companion object
}
