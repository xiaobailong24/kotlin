// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR
// JVM IR status: throwing exception because of FE, see KT-47621
// WITH_STDLIB
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: allowing this code, runtime StackOverflowError

// FILE: j/Region.java
package j;

public class Region {
    protected void setWidth(double value) {}
    public final double getWidth() { return 1.0; }
}

// FILE: j/Button.java
package j;

public class Button extends Region {
}

// FILE: j/HBox.java
package j;

public class HBox extends Region {}

// FILE: main.kt
import j.Button
import j.HBox

class Door {
    var button: Button = Button()
}

class GatesPanel : HBox() {
    val doorList = mutableListOf<Door>()

    private fun prepare() {
        for (door in doorList) {
            door.button?.width = 10.0 // No error, crash on JVM IR
        }
    }
}

fun box() = "OK"
