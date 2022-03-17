fun <T> getT(): T = null!!

val foo = getT<List>()
/*
psi: val foo = getT<List>()
type: [Error type: Not found recorder type for getT<List>()]
*/