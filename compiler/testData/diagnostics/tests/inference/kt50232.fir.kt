fun main() {
    val x = inferType()
    acceptEnum(inferType())
    acceptEnum(x)
    acceptEnum2(inferType(), inferType())
}

fun acceptEnum(value: MyEnum?) {}
fun acceptEnum2(value: MyEnum?, value2: MyEnum?) {}

fun <R : Enum<R>?> inferType(): R = TODO()

enum class MyEnum {
    VALUE
}