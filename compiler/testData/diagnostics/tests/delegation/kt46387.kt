// WITH_STDLIB

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Delegater {
    inline operator fun <reified T : Any?> provideDelegate(receiver: Any?, property: KProperty<*>): ReadWriteProperty<Any?, T?> = TODO()
}

class MyClass(val delegater: Delegater) {
    private val delegated: String? by <!DELEGATE_SPECIAL_FUNCTION_MISSING, DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>delegater<!>
}
