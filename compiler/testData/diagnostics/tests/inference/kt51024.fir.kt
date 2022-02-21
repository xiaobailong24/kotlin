// WITH_STDLIB
// FULL_JDK

// FILE: Test1.java
public class Test1 implements Comparable<Test1> {
    @Override
    public int compareTo(Test1 o) {
        return 0;
    }
}

// FILE: main.kt
class Foo<K>(x: K)
class Bar<T : Comparable<T>>(x: Foo<T>)

fun <E> select(vararg x: E) = x[0]

class Test2: Comparable<Test2> {
    override fun compareTo(other: Test2): Int = TODO()
}

fun test(x: Test1, y: Test2) {
    <!NEW_INFERENCE_ERROR!>select(Bar(Foo(x)), Bar(Foo(y)),)<!>
}