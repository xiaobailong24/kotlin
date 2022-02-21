interface I1
interface I2

fun <Obj> foo(): Obj where Obj : I1, Obj: I2 {
    return <!TYPE_MISMATCH("Obj; <no name provided><Obj>")!>object : I1, I2<!> { }
}