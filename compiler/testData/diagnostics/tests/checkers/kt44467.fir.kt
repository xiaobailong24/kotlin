interface I1
interface I2

fun <Obj> foo(): Obj where Obj : I1, Obj: I2 {
    return <!RETURN_TYPE_MISMATCH!>object : I1, I2 { }<!>
}