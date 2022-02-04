class Cell {
    operator fun get(s: Int) = 1
}

fun box(): String {
    val c = Cell()
    (c[0])++
    return "OK"
}