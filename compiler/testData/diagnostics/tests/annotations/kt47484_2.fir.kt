interface I {
    fun <T
            > f<!SYNTAX!><!> = "".
    (C().<!FUNCTION_CALL_EXPECTED!>f<!><!SYNTAX!><!>
    class C : I<!SYNTAX!><!>
