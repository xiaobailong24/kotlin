fun NULLIBILITY_INFERENCE() {
    val a: Throwable? = null;
    val b: Unit? = null
    val c = a ?: b?.let { return it } ?: return // compiler correctly infers that `c` is not null
    c<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
    throw a // thus, `a` can't be null here
}