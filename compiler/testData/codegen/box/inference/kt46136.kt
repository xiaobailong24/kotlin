// WITH_STDLIB

// Case 1

fun instantDeserialiser() = <!RETURN_TYPE_MISMATCH("")!>deserialiser { parser -> "" }<!>

interface JsonDeserializer<T> {
    fun deserialize(p: String): T
}

private fun <T> deserialiser(by: (String) -> T) =
    object : JsonDeserializer<T> {
        override fun deserialize(p: String): T = TODO()
    }

// Case 2

abstract class A<T>

fun f() = <!RETURN_TYPE_MISMATCH("")!>test("")<!>

private fun <T> test(t: T) =
    object : A<T>() {}

fun box() = "OK"