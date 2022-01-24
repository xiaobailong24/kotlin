// WITH_STDLIB

fun <T> MutableList<T>.customFun(element: T) {
}

var <T> MutableList<T>.customAccessor: T
    get() {
        return get(0)
    }
    set(value) {
        add(value)
    }

fun main() {
    val mutableList: MutableList<Int> = mutableListOf()
    val list: MutableList<out Any> = mutableList
    list.customFun(<!TYPE_MISMATCH!>Any()<!>)       // Type mismatch compilation error as expected
    list.customAccessor = Any() // There is no compilation error :(
    println(mutableList.first()) // ClassCastException!
}
