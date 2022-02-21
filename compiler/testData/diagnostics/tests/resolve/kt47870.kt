// FILE: example/JavaSuper.java
package example;

public abstract class JavaSuper {
    protected @interface Foo {
        String value() default "";
    }
}

// FILE: main.kt
import example.JavaSuper

class KotlinChildOfJavaSuper : JavaSuper() {
    @<!INVISIBLE_MEMBER!>Foo<!>("should work") // [INVISIBLE_MEMBER] Cannot access '<init>': it is protected in 'Foo'
    fun usesFoo() = ""
}