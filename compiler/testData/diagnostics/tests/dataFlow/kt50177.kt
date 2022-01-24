// FIR_IDENTICAL
// WITH_STDLIB

fun name(input: List<Int>): Int =
    input.sumOf {
        0 <!USELESS_CAST!>as Int<!>
    }