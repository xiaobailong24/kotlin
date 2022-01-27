class Foo
class Bar<T1: Foo, out T2>
class Baz<T1, T2: Bar<Foo, out T2>>
class Qux<T1, T2: Baz<T2, Bar<Foo, T2>>>(var f: T2)

class Quux<T> {

    fun test(): Unit {
        val x: Qux<in T, Baz<T, Bar<Foo, in T>>> = TODO()
        x.f = TODO()
    }
}