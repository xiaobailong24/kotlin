// IGNORE_BACKEND: JVM_IR
// JVM IR status: throwing exception because of FE, see KT-47381
// IGNORE_BACKEND_FIR: JVM_IR
// FIR status: reported proper error, move into diagnostic tests after fixing the BE exception

var globalA: A = TODO()

class A

var A.prop get() = this

// -----

class A1

var i: Int = TODO()

var A1.i: Int
    get() = 0