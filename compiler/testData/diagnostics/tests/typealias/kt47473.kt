// WITH_STDLIB

class NumColl<T : Collection<Number>>

typealias NL<T2> = NumColl<List<T2>>
typealias MMMM<A3> = NL<A3>

val falseUpperBoundViolation = MMMM<Int>() // Shouldn't be error
val missedUpperBoundViolation = NL<Any>()  // Should be error