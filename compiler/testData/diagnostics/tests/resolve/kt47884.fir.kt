interface A<X>
class B<T>

fun foo(a: A<*>, b: B<*>): Boolean = <!EQUALITY_NOT_APPLICABLE_WARNING!>a == b<!>
