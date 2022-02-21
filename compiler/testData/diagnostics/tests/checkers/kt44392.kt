fun foo1(a: Any?) {
    (a as String?)!!
    (a <!USELESS_CAST!>as? String<!>)!!
}

fun foo2(a: Any?) {
    (a as String?)!!
    <!DEBUG_INFO_SMARTCAST!>a<!>!!
}