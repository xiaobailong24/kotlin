// WITH_STDLIB

sealed class MyBoolean {

    object False : MyBoolean()
    object True : MyBoolean()

    fun merge(other: MyBoolean) = when(other) {
        is True -> this
        is False -> False
    }
}

// This compiles fine
fun Collection<MyBoolean>.reduce() =
    this.fold(MyBoolean.True, MyBoolean::merge)

// This does not
// Type mismatch: inferred type is MyBoolean but MyBoolean.True was expected
fun Collection<MyBoolean>.reduce2() =
    this.fold(MyBoolean.True) { acc, e ->
        acc.merge(e)
    }