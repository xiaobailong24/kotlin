// FIR_IDENTICAL
import FooOperation.*

interface Operation<T>

class FooOperation(val foo: String) : Operation<Boom> {
    @kotlin.Suppress("test") // Unresolved reference: Suppress
    class Boom(val bar: String)
}