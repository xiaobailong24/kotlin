import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

fun <T> switchIt(value: T) {
    when (value) {
        is String -> Json.encodeToString(value)
    }
}

fun box(): String {
    switchIt("")
    return "OK"
}
