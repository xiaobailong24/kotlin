fun test(a: Any, b: Any, s: String, i: Int) {
    if (a is String && b is Int) {
        <!EQUALITY_NOT_APPLICABLE!>a == b<!> // OK, but it shouldn't be allowed
    }
    <!EQUALITY_NOT_APPLICABLE!>s == i<!> // error: EQUALITY_NOT_APPLICABLE
}