// ISSUE: KT-41430, KT-47830

class A

fun test_1(list: List<Set<A>>) {
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.collections.MutableSet<A>")!>list.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>flatMapTo(mutableSetOf()) { it }<!><!>
}

fun test_2(list: List<Set<A>>) {
    sequence<A> {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.collections.MutableSet<A>")!>list.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>flatMapTo(mutableSetOf()) { it }<!><!>
    }
}

fun test_3(list: List<Set<A>>) {
    sequence {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.collections.MutableSet<A>")!>list.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>flatMapTo(mutableSetOf()) { it }<!><!>
        yield(A())
    }
}

fun test_4(list: List<Set<A>>) {
    sequence {
        yield(A())
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.collections.MutableSet<A>")!>list.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>flatMapTo(mutableSetOf()) { it }<!><!>
    }
}
