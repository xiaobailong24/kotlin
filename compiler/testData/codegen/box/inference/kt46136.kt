// IGNORE_BACKEND: JVM_IR
// JVM IR status: throwing exception because of FE, see KT-46136
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: reported proper error, move into diagnostic tests after fixing the BE exception

// WITH_STDLIB

// Case 1

fun instantDeserialiser() = deserialiser { parser -> "" }

interface JsonDeserializer<T> {
    fun deserialize(p: String): T
}

private fun <T> deserialiser(by: (String) -> T) =
    object : JsonDeserializer<T> {
        override fun deserialize(p: String): T = TODO()
    }

// Case 2

abstract class A<T>

fun f() = test("")

private fun <T> test(t: T) =
    object : A<T>() {}

fun box() = "OK"