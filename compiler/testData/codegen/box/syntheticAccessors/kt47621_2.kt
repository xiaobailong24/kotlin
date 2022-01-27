// WITH_STDLIB

// FILE: j/J.java
package j;

public class J {
    public int getX() { return 1; }
    protected void setX(int value) { throw new RuntimeException(); }
}

// FILE: main.kt
import j.*

class C : J() {
    fun foo(): String {
        J()?.x = 1
        return "OK"
    }
}

fun box() = C().foo()