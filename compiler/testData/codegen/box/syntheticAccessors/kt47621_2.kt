// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR
// JVM IR status: throwing exception because of FE, see KT-47621
// WITH_STDLIB
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: allowing this code, runtime StackOverflowError

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