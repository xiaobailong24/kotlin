// WITH_STDLIB

class Node(val parent: Node?)

fun superParent(a: Node?): Node? {
    check(a != null)
    var p = a<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!> // Here is "unnecessary non-null assertion"
    while (p.parent != null) p = <!DEBUG_INFO_SMARTCAST!>a<!>.parent!!
    return p
}

fun superParent2(a: Node?): Node? {
    check(a != null)
    var p = a
    while (p<!UNSAFE_CALL!>.<!>parent != null) p = <!DEBUG_INFO_SMARTCAST!>a<!>.parent!!
    return p
}