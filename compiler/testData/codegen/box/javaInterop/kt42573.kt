// FILE: TestJava.java
public class TestJava {
    public static String op(Object object) { return "NOK"; }
    public static String op(Object[] objects) { return "OK"; }
}

// FILE: main.kt
fun box(): String {
    var i: Int? = 1
    i = null
    return TestJava.op(i)
}
