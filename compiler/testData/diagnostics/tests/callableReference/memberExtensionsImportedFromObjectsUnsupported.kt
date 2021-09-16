// !DIAGNOSTICS: -UNUSED_EXPRESSION

import Obj.ext
import A.Companion.ext2

object Obj {
    val String.ext: String get() = this
}

class A {
    companion object {
        val String.ext2: String get() = this
    }
}

fun <K> id(x: K) = x

fun foo(x: Int): Unit

fun test() {
    id(::foo)

//    String::<!EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED!>ext<!>
//    Obj::<!EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED, UNRESOLVED_REFERENCE_WRONG_RECEIVER!>ext<!>
//
//    String::<!EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED!>ext2<!>
//    A.Companion::<!EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED, UNRESOLVED_REFERENCE_WRONG_RECEIVER!>ext2<!>
//    A::<!EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED, UNRESOLVED_REFERENCE_WRONG_RECEIVER!>ext2<!>
//
//    A::<!UNRESOLVED_REFERENCE!>foo<!>
//    A::<!UNRESOLVED_REFERENCE!>bar<!>
}
