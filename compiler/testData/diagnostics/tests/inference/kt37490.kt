interface SomeFace<T>

class SomeGene<T> {
    fun setFace(face: SomeFace<T>?) {}
    fun setString(string: String?) {}
}

fun nullTypeArg(invP: SomeGene<Any>, inP: SomeGene<in Any>, outP: SomeGene<out Any>) {
    invP.setFace(null) // no error
    invP.setString(null) // no error
    inP.setFace(<!NULL_FOR_NONNULL_TYPE!>null<!>) // NULL_FOR_NONNULL_TYPE: Null can not be a value of a non-null type Nothing
    inP.setString(null) // no error
    outP.setFace(<!NULL_FOR_NONNULL_TYPE!>null<!>) // NULL_FOR_NONNULL_TYPE: Null can not be a value of a non-null type Nothing
    outP.setString(null) // no error
}
