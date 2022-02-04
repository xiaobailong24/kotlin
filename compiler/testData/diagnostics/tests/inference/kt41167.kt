fun <T> mock(): T = TODO()
fun f() {
    val arr1 /* : Array<String> */ = Array<String>(5) { mock() }
    val arr2/* : Array<String> */ = Array(5) { mock<String>() }
    val arr3: Array<String> = Array(5) { mock() } // TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER
}