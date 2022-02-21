class Test<T: <!FINAL_UPPER_BOUND!>Byte<!>> {
    fun test(z: T) {
        val y: Byte = if (true) z else 1 // works
        val x = bar(<!ARGUMENT_TYPE_MISMATCH!>if (true) z else 1<!>) // fails
    }

    fun bar(x: Byte) {}
}