typealias ToTypeParam1<T> = <!TYPEALIAS_SHOULD_EXPAND_TO_CLASS!>T<!>
typealias ToTypeParam2<T> = <!TYPEALIAS_SHOULD_EXPAND_TO_CLASS!>ToTypeParam1<out T><!>
