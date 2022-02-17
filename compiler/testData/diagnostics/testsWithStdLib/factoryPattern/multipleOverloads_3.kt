// FIR_IDENTICAL
// !LANGUAGE: +NewInference +OverloadResolutionByLambdaReturnType
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE -UNUSED_EXPRESSION -OPT_IN_USAGE -EXPERIMENTAL_UNSIGNED_LITERALS
// ISSUE: KT-11265

// FILE: OverloadResolutionByLambdaReturnType.kt

package kotlin

annotation class OverloadResolutionByLambdaReturnType

// FILE: main.kt

import kotlin.OverloadResolutionByLambdaReturnType

public inline fun <T, R> Iterable<T>.myFlatMap(transform: (T) -> Iterable<R>): List<R> {
    TODO()
}

@OverloadResolutionByLambdaReturnType
@kotlin.jvm.JvmName("myFlatMapSequence")
public inline fun <T, R> Iterable<T>.myFlatMap(transform: (T) -> Sequence<R>): List<R> {
    TODO()
}

interface Name
interface DeclarationDescriptor {
    val nextCandidates: List<DeclarationDescriptor>?
    val nextCandidatesSeq: Sequence<DeclarationDescriptor>?
    val name: Name
}

fun test_1(name: Name, toplevelDescriptors: List<DeclarationDescriptor>): List<DeclarationDescriptor> {
    val candidates = toplevelDescriptors.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>myFlatMap { container ->
        val nextCandidates = container.nextCandidates ?: return@myFlatMap emptyList()
        nextCandidates
    }<!>
    return candidates
}

fun test_2(name: Name, toplevelDescriptors: List<DeclarationDescriptor>): List<DeclarationDescriptor> {
    val candidates = toplevelDescriptors.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>myFlatMap { container ->
        val nextCandidates = container.nextCandidatesSeq ?: return@myFlatMap <!TYPE_MISMATCH, TYPE_MISMATCH!>sequenceOf()<!>
        <!TYPE_MISMATCH, TYPE_MISMATCH!>nextCandidates<!>
    }<!>
    return <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>candidates<!>
}

fun test_3(name: Name, toplevelDescriptors: List<DeclarationDescriptor>): List<DeclarationDescriptor> {
    val candidates = toplevelDescriptors.<!CANDIDATE_CHOSEN_USING_OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION!>myFlatMap { container ->
        val nextCandidates = container.nextCandidatesSeq!!
        <!TYPE_MISMATCH, TYPE_MISMATCH!>nextCandidates<!>
    }<!>
    return <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>candidates<!>
}
