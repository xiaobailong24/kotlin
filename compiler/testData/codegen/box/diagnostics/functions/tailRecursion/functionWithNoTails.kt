// DONT_TARGET_EXACT_BACKEND: WASM
// WASM_MUTE_REASON: IGNORED_IN_JS
// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6
// IGNORE_FIR_DIAGNOSTICS_DIFF

<!NO_TAIL_CALLS_FOUND!>tailrec fun noTails()<!> {
    // nothing here
}

fun box(): String {
    noTails()
    return "OK"
}
