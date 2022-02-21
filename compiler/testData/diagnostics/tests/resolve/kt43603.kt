import kotlin.reflect.KClass

interface A

class B : A

open class C : A

val <T : C> KClass<T>.extProp
    get() = "I'm C"

val A.extProp
    get() = when (this) {
        is B -> "I'm B"
        is C -> (this <!USELESS_CAST!>as C<!>)::class.extProp
        else -> ""
    }
