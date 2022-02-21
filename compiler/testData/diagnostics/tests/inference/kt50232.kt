fun main() {
    val x = inferType()
    acceptEnum(<!TYPE_MISMATCH_WARNING!>inferType<!>())
    acceptEnum(<!TYPE_MISMATCH!>x<!>)
    acceptEnum2(<!TYPE_MISMATCH!>inferType<!>(), inferType())
}

fun acceptEnum(value: MyEnum?) {}
fun acceptEnum2(value: MyEnum?, value2: MyEnum?) {}

fun <R : Enum<R>?> inferType(): R = TODO()

enum class MyEnum {
    VALUE
}