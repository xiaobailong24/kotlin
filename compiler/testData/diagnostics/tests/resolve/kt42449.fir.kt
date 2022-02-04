class NodePropertyDescriptor<TNode : <!FINAL_UPPER_BOUND!>Node<!>, TProperty : Any, TPropertyVal : TProperty?>(
    val description: String,
    val propertyRef: NodePropertyRef<TNode, TProperty, TPropertyVal>,
)

fun foo(other: NodePropertyDescriptor<*, *, *>) {
    other.description
    other.propertyRef
}

class NodePropertyRef<T, U, V>
class Node