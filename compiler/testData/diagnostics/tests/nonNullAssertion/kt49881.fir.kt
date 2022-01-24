fun test(): Unit {
    val exprDomains = mutableMapOf<Base, Set<Value>>()

    fun run(expr: SubClass) {
        exprDomains[expr] = setOf([expr.domain]!!)
    }
}

abstract class Base
data class SubClass(val domain: String): Base()
class Value