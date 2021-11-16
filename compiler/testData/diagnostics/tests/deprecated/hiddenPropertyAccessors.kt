var v4: String
    get() = ""
    @Deprecated("", level = DeprecationLevel.HIDDEN)
    set(value) {}

fun test() {
    <!DEPRECATION_ERROR!>v4<!> = ""
}
