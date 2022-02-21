// IGNORE_BACKEND: JVM_IR
// JVM IR status: throwing exception because of FE, see KT-28109
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: reported proper error, move into diagnostic tests after fixing the BE exception

class Cell {
    operator fun get(s: Int) = 1
}

fun box(): String {
    val c = Cell()
    (c[0])++
    return "OK"
}