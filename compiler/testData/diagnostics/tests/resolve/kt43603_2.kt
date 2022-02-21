import kotlin.reflect.KClass

interface A

class B : A

open class C : A

val <T : C> KClass<T>.extProp
    get() = "I'm C"

val A.extProp
    get() = when (this) {
        is B -> "I'm B"
        is C -> <!TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM_ERROR!>this::class.<!DEBUG_INFO_MISSING_UNRESOLVED!>extProp<!><!>
        else -> ""
    }
