class NodePropertyDescriptor<TNode : <!FINAL_UPPER_BOUND!>Node<!>, TProperty : Any, TPropertyVal : TProperty?>(
    val description: String,
    val propertyRef: NodePropertyRef<TNode, TProperty, TPropertyVal>,
)

fun foo(other: NodePropertyDescriptor<*, *, *>) {
    other.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>description<!>
    other.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>propertyRef<!>
}

class NodePropertyRef<T, U, V>
class Node