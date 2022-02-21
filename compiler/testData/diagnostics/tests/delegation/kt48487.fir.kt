// WITH_STDLIB

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val a by object : ReadOnlyProperty<Any?, Double> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Double {
        return Math.random()
    }
}.let {
    object : ReadOnlyProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return it.getValue(thisRef, property).toInt()
        }
    }
}