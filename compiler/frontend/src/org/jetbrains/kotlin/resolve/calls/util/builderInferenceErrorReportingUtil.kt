/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.util

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.components.candidate.ResolutionCandidate
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCallAtom
import org.jetbrains.kotlin.resolve.calls.model.ReceiverExpressionKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.ReceiverKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.calls.tower.NewAbstractResolvedCall
import org.jetbrains.kotlin.resolve.calls.tower.psiExpression
import org.jetbrains.kotlin.resolve.calls.tower.psiKotlinCall
import org.jetbrains.kotlin.resolve.calls.tower.receiverValue
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.AbstractStubType
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.ClassicTypeCheckerState
import org.jetbrains.kotlin.types.checker.ClassicTypeCheckerStateInternals
import org.jetbrains.kotlin.types.typeUtil.contains

private typealias CandidatesByStubTypeArgumentConstraints =
        Map<ResolutionCandidate, List<Pair<ArgumentBasedConstraintPosition<*>, KotlinType>>>

private fun ArgumentBasedConstraintPosition<*>.extractPsiExpression(resolvedCall: MutableResolvedCallAtom) =
    when (this) {
        is ArgumentConstraintPositionImpl -> argument.psiExpression
        is ReceiverConstraintPositionImpl -> argument.psiExpression ?: resolvedCall.atom.psiKotlinCall.psiCall.callElement
        else -> null
    }

private fun ArgumentBasedConstraintPosition<*>.extractArgumentType() = (argument as? ReceiverKotlinCallArgument)?.receiverValue?.type

private fun ArgumentBasedConstraintPosition<*>.extractFartherReceiverOwnerKtFunction(scope: LexicalScope): BuilderLambdaLabelingInfo {
    if (this !is ReceiverConstraintPositionImpl) return BuilderLambdaLabelingInfo.EMPTY

    val argument = argument as? ReceiverExpressionKotlinCallArgument ?: return BuilderLambdaLabelingInfo.EMPTY
    val receiverValue = argument.receiverValue as? ExtensionReceiver ?: return BuilderLambdaLabelingInfo.EMPTY
    val nearestScopeDescriptor = scope.ownerDescriptor

    return if (nearestScopeDescriptor != receiverValue.declarationDescriptor) {
        val ktLambda = receiverValue.declarationDescriptor.source.getPsi() as? KtFunctionLiteral ?: return BuilderLambdaLabelingInfo.EMPTY
        BuilderLambdaLabelingInfo(ktLambda)
    } else BuilderLambdaLabelingInfo.EMPTY
}

private fun buildCandidatesByStubTypeArgumentConstraints(
    candidates: Collection<ResolutionCandidate>
): CandidatesByStubTypeArgumentConstraints? =
    candidates.associateWith { candidate ->
        candidate.getSystem().getBuilder().currentStorage().initialConstraints.mapNotNull { constraint ->
            val position = constraint.position as? ArgumentBasedConstraintPosition<*> ?: return@mapNotNull null
            if (!(constraint.a as KotlinType).contains { it is AbstractStubType }) return@mapNotNull null
            position to constraint.b as KotlinType
        }
    }
        .filterValues { it.isNotEmpty() }
        .takeIf { it.size > 1 } // Don't take 1 or 0 candidates because it's a not stub types related resolution ambiguity

private fun buildArgumentsByInvolvedStubTypeAmbiguityInfo(
    candidatesByStubTypeArgumentConstraints: CandidatesByStubTypeArgumentConstraints,
    scope: LexicalScope
): Map<KtElement, StubTypeAmbiguityValueArgumentInfo> = buildMap {
    for ((candidate, stubTypeConstraints) in candidatesByStubTypeArgumentConstraints) {
        for (constraintInfo in stubTypeConstraints) {
            val (constraintPosition, constrainingType) = constraintInfo
            val valueArgument = constraintPosition.extractPsiExpression(candidate.resolvedCall) ?: continue
            val expectedType = constrainingType.let {
                val constructor = it.constructor as? TypeVariableTypeConstructor ?: return@let it
                candidate.resolutionCallbacks.findResultType(candidate.getSystem(), constructor) ?: it
            }

            val existingValueArgumentInfo = get(valueArgument)

            if (existingValueArgumentInfo != null) {
                existingValueArgumentInfo.possibleSpecificTypes.add(expectedType)
                continue
            }

            val argumentType = constraintPosition.extractArgumentType() ?: continue
            val receiverOwnerLambdaInfo = constraintPosition.extractFartherReceiverOwnerKtFunction(scope)
            val stubTypeAmbiguityValueArgumentInfo = StubTypeAmbiguityValueArgumentInfo(
                argumentType, mutableListOf(expectedType), constraintPosition is ReceiverConstraintPosition<*>, receiverOwnerLambdaInfo
            )

            put(valueArgument, stubTypeAmbiguityValueArgumentInfo)
        }
    }
}

@OptIn(ClassicTypeCheckerStateInternals::class)
private val typeCheckerState = ClassicTypeCheckerState(isErrorTypeEqualsToAnything = true)

@OptIn(ClassicTypeCheckerStateInternals::class)
private fun areThereSubtypingUnrelatedTypes(typeCandidates: Collection<KotlinType>): Boolean =
    typeCandidates.any { first ->
        typeCandidates.any { second ->
            !AbstractTypeChecker.isSubtypeOf(typeCheckerState, first, second)
                    && !AbstractTypeChecker.isSubtypeOf(typeCheckerState, second, first)
        }
    }

private fun reportStubTypeRelatedResolutionAmbiguity(
    argumentsToInvolvedStubTypeConstraints: Map<KtElement, StubTypeAmbiguityValueArgumentInfo>,
    tracingStrategy: TracingStrategy,
    trace: BindingTrace
): Boolean {
    var didStubTypeCauseAmbiguity = false

    for ((argument, ambiguityValueArgumentInfo) in argumentsToInvolvedStubTypeConstraints) {
        val typeCandidates = ambiguityValueArgumentInfo.possibleSpecificTypes.takeIf { it.size > 1 } ?: continue

        if (areThereSubtypingUnrelatedTypes(typeCandidates)) {
            didStubTypeCauseAmbiguity = true
            tracingStrategy.stubTypeCausesAmbiguity(
                trace, argument, ambiguityValueArgumentInfo.argumentType, typeCandidates,
                ambiguityValueArgumentInfo.builderLambdaAvailableOnlyThroughLabeledReceiver, ambiguityValueArgumentInfo.isExtensionReceiver
            )
        }
    }

    return didStubTypeCauseAmbiguity
}

internal fun <D : CallableDescriptor> reportStubTypeRelatedResolutionAmbiguityIfNeeded(
    candidates: Collection<ResolutionCandidate>,
    resolvedCalls: List<NewAbstractResolvedCall<D>>,
    tracingStrategy: TracingStrategy,
    trace: BindingTrace,
    scope: LexicalScope
): Boolean {
    val candidatesByStubTypeArgumentConstraints = buildCandidatesByStubTypeArgumentConstraints(candidates) ?: return false
    val argumentsToInvolvedStubTypeConstraints =
        buildArgumentsByInvolvedStubTypeAmbiguityInfo(candidatesByStubTypeArgumentConstraints, scope)
    val wasStubTypeCausedAmbiguity =
        reportStubTypeRelatedResolutionAmbiguity(argumentsToInvolvedStubTypeConstraints, tracingStrategy, trace)

    if (wasStubTypeCausedAmbiguity) {
        tracingStrategy.ambiguityBecauseOfStubTypes(trace, resolvedCalls)
    }

    return wasStubTypeCausedAmbiguity
}

private data class StubTypeAmbiguityValueArgumentInfo(
    val argumentType: KotlinType,
    val possibleSpecificTypes: MutableList<KotlinType>,
    val isExtensionReceiver: Boolean,
    /*
     For instance:

         fun Int.foo() {}
         fun String.foo() {}

         buildList {
             add("one")
             with (get(0)) {
                 with (listOf(1)) { foo() }
             }
         }

     In this case implicit receiver of `foo` should be marked with the corresponding lambda to make possible to suggest the quick fix by IDE:
        with (get(0)) { ... } -> with (get(0)) l1@ { ... }
        foo() -> (this@l1 as String).foo()
     */
    val builderLambdaAvailableOnlyThroughLabeledReceiver: BuilderLambdaLabelingInfo
)

class BuilderLambdaLabelingInfo(val builderLambda: KtFunctionLiteral?) {
    companion object {
        val EMPTY = BuilderLambdaLabelingInfo(null)
    }
}
