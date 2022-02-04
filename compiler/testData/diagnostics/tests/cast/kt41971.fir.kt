sealed class Foo<T>
class Foo1<A> : Foo<Int>()
class Foo2    : Foo<Long>()

fun process(foo: Foo<Long>) {
    val result = when (foo) {
        is Foo1<*> -> 42  // Missing "Incompatible types" error
        is Foo2    -> 128
    }
    if (foo is Foo1<*>) { // Missing "Incompatible types" error
        process(<!ARGUMENT_TYPE_MISMATCH!>foo <!CAST_NEVER_SUCCEEDS!>as<!> Foo1<*><!>) // Missing CAST_NEVER_SUCCEEDS, but correctly reports "Type mismatch" for the call
    }
    foo <!CAST_NEVER_SUCCEEDS!>as<!> Foo1<*> // Missing CAST_NEVER_SUCCEEDS
}