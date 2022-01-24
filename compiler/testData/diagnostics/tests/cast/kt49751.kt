fun foo(x: I) {
    if (x is K) {
        test(x as K) //this unnecessary cast is not reported
    }
}
fun foo(x: Any) {
    if (x is K) {
        test(x as K) // [USELESS_CAST] No cast needed
    }
}
fun test(o: K) {}
interface I {}
interface K {}