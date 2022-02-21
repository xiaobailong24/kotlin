fun foo1(a: Any?) {
    (a as String?)!!
    (a as? String)!!
}

fun foo2(a: Any?) {
    (a as String?)!!
    a<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
}