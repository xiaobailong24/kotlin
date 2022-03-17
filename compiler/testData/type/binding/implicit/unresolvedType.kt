fun <T> getT(): T = null!!

val foo = getT<List<adad<List<dd>>>()
/*
psi: val foo = getT<List<adad<List<dd>>>()
type: [Error type: Not found recorder type for getT<List<adad<List<dd>>>()]
*/