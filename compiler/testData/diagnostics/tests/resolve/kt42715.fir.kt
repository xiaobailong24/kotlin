// FULL_JDK
// WITH_STDLIB

// Case 1

import java.io.File

fun predicate(file: File): Boolean = file.name.startsWith("x")

fun xFiles(dir: File): Array<File>? = dir.listFiles { file -> predicate(file) }

// Case 2

fun foo(x: (a: Int) -> Unit) {} // 1
fun foo(x: (a: Int, b: String) -> Unit) {} // 2

fun main() {
    foo { x -> } // OK, resolved to (1)
    foo { x, y -> } // OK, resolved to (2)
    foo { } // ERROR, but should be resolved to `foo` considering `a` as implicit `it`
}