class Foo(var pickaxe: Function0<Function1<Char, Any>>)

interface I {
    fun m(): Int
}

fun test() {
    val x = Foo({ -> fun (p: Char): Any {
        fun inner_m(): Any {
            val x: I = TODO()
            x::m
        }
        return inner_m()
    }}
    )
}