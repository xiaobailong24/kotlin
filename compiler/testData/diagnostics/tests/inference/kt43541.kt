// WITH_STDLIB

object Test {

    @JvmStatic
    fun main(args: List<String>) {

        val b: Boolean = <!TYPE_MISMATCH!><!TYPE_MISMATCH!>number<!>()<!> // compiles, but shouldn't
        booleanFunction(number()) // also compiles, but shouldn't


        booleanFunction(Generic<<!UPPER_BOUND_VIOLATED!>Boolean<!>>().number()) // needs explicit type, doesn't compile
        booleanFunction(<!TYPE_MISMATCH!>Generic<Int>().number()<!>) // needs explicit type, doesn't compile

    }

    fun <T : Number> number(): T = 4 <!UNCHECKED_CAST!>as T<!>

    private fun booleanFunction(b: Boolean) {
        println(b)
    }

    class Generic<T: Number> {
        fun number(): T = 4 <!UNCHECKED_CAST!>as T<!>
    }
}
