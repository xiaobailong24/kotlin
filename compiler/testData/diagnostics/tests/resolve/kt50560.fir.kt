// FILE: BaseColumns.java
public interface BaseColumns {
    String _ID = "_id";
}

// FILE: main.kt
interface SomeClass : BaseColumns {
    companion object {
        const val FOO_CONST = 123
    }
}

class KotlinClass {
    fun foo() {
        val test = SomeClass._ID
        val test2 = SomeClass.FOO_CONST
    }
}