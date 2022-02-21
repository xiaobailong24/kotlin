// FILE: SAM.java
public interface SAM<T> {
    void apply(T x);
}

// FILE: A.java
public class A<T> {
    public void call(SAM<T>... block) { block[0].apply(null); }
}

// FILE: B.java
public class B<T> {
    public void call(SAM<T> block) { block[0].apply(null); }
}

// FILE: main.kt
fun f(x: A<*>, x2: B<*>) {
    x.call <!VARARG_OUTSIDE_PARENTHESES!>{ y: Any? -> Unit }<!>
    x.call(<!TYPE_MISMATCH!>{ y: Any? -> Unit }<!>)
    x.call <!VARARG_OUTSIDE_PARENTHESES!>{ y: Nothing -> Unit }<!>
    x.call({ <!EXPECTED_PARAMETER_TYPE_MISMATCH!>y: Nothing<!> -> Unit })
    x.call <!VARARG_OUTSIDE_PARENTHESES!>{ <!CANNOT_INFER_PARAMETER_TYPE!>y<!> -> Unit }<!>
    x.call(<!TYPE_MISMATCH!>{ <!CANNOT_INFER_PARAMETER_TYPE!>y<!> -> Unit }<!>)

    x2.call { y: Any? -> Unit }
    x2.call { <!EXPECTED_PARAMETER_TYPE_MISMATCH!>y: Nothing<!> -> Unit }
    x2.call { y -> Unit }
}
