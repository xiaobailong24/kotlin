/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.resolve.calls.components.*
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.descriptorUtil.OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.utils.addToStdlib.cast


class KotlinCallResolver(
    private val towerResolver: TowerResolver,
    private val kotlinCallCompleter: KotlinCallCompleter,
    private val overloadingConflictResolver: NewOverloadingConflictResolver,
    private val callableReferenceResolver: CallableReferenceResolver,
    private val callComponents: KotlinCallComponents
) {
    fun resolveCallableReference(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        factory: CandidateFactory<KotlinResolutionCandidate>
    ): Set<CallableReferenceCandidate> {
        val processor = createCallableReferenceProcessor(factory as CallableReferencesCandidateFactory)
        val candidates = towerResolver.runResolve(scopeTower, processor, useOrder = true, name = kotlinCall.name)

        return callableReferenceResolver.callableReferenceOverloadConflictResolver.chooseMaximallySpecificCandidates(
            candidates,
            CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
            discriminateGenerics = false // we can't specify generics explicitly for callable references
        )
    }

    fun createFactory(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        resolutionCallbacks: KotlinResolutionCallbacks,
        expectedType: UnwrappedType?,
        baseCallContext: BaseCallContext? = null
    ): CandidateFactory<KotlinResolutionCandidate> {
        return if (kotlinCall.callKind == KotlinCallKind.CALLABLE_REFERENCE) {
            val resolutionAtom = baseCallContext?.argument
                ?: CallableReferenceKotlinCall(kotlinCall, resolutionCallbacks.getLhsResult(kotlinCall), kotlinCall.name)

            CallableReferencesCandidateFactory(
                resolutionAtom, callComponents, scopeTower, expectedType, resolutionCallbacks, callableReferenceResolver, baseCallContext = baseCallContext
            )
        } else {
            SimpleCandidateFactory(
                callComponents, scopeTower, kotlinCall, resolutionCallbacks, callableReferenceResolver
            )
        }
    }

    fun resolveAndCompleteCall(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        collectAllCandidates: Boolean,
    ): CallResolutionResult {
        val candidateFactory = createFactory(scopeTower, kotlinCall, resolutionCallbacks, expectedType)
        val candidates = resolveCall(
            scopeTower, resolutionCallbacks, kotlinCall, expectedType, collectAllCandidates, null, candidateFactory
        )

        if (collectAllCandidates) {
            return kotlinCallCompleter.createAllCandidatesResult(candidates, expectedType, resolutionCallbacks)
        }

        return kotlinCallCompleter.runCompletion(candidateFactory, candidates, expectedType, resolutionCallbacks)
    }

    fun <C : KotlinResolutionCandidate> resolveCall(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        collectAllCandidates: Boolean,
        baseCallContext: BaseCallContext? = null,
        candidateFactory: CandidateFactory<C> = createFactory(
            scopeTower, kotlinCall, resolutionCallbacks, expectedType, baseCallContext
        ).cast(),
    ): Collection<C> {
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        kotlinCall.checkCallInvariants()

        val processor: ScopeTowerProcessor<C> = when (kotlinCall.callKind) {
            KotlinCallKind.VARIABLE -> {
                createVariableAndObjectProcessor(scopeTower, kotlinCall.name, candidateFactory, kotlinCall.explicitReceiver?.receiver)
            }
            KotlinCallKind.FUNCTION -> {
                createFunctionProcessor(
                    scopeTower,
                    kotlinCall.name,
                    candidateFactory,
                    resolutionCallbacks.getCandidateFactoryForInvoke(scopeTower, kotlinCall),
                    kotlinCall.explicitReceiver?.receiver
                ).cast()
            }
            KotlinCallKind.CALLABLE_REFERENCE -> {
                createCallableReferenceProcessor(candidateFactory as CallableReferencesCandidateFactory).cast()
            }
            KotlinCallKind.INVOKE -> {
                createProcessorWithReceiverValueOrEmpty(kotlinCall.explicitReceiver?.receiver) {
                    createCallTowerProcessorForExplicitInvoke(
                        scopeTower,
                        candidateFactory,
                        kotlinCall.dispatchReceiverForInvokeExtension?.receiver as ReceiverValueWithSmartCastInfo,
                        it
                    )
                }
            }
            KotlinCallKind.UNSUPPORTED -> throw UnsupportedOperationException()
        }

        if (collectAllCandidates) {
            val allCandidates = towerResolver.collectAllCandidates(scopeTower, processor, kotlinCall.name)
            return allCandidates
        }

        val candidates = towerResolver.runResolve(
            scopeTower,
            processor,
            useOrder = kotlinCall.callKind != KotlinCallKind.UNSUPPORTED,
            name = kotlinCall.name
        )

        return choseMostSpecific(kotlinCall, resolutionCallbacks, candidates)
    }

    fun resolveGivenCandidates(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        givenCandidates: Collection<GivenCandidate>,
        collectAllCandidates: Boolean
    ): CallResolutionResult {
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        kotlinCall.checkCallInvariants()
        val candidateFactory = SimpleCandidateFactory(
            callComponents, scopeTower, kotlinCall, resolutionCallbacks, callableReferenceResolver
        )

        val resolutionCandidates = givenCandidates.map { candidateFactory.createCandidate(it).forceResolution() }

        if (collectAllCandidates) {
            val allCandidates = towerResolver.runWithEmptyTowerData(
                KnownResultProcessor(resolutionCandidates),
                TowerResolver.AllCandidatesCollector(),
                useOrder = false
            )
            return kotlinCallCompleter.createAllCandidatesResult(allCandidates, expectedType, resolutionCallbacks)

        }
        val candidates = towerResolver.runWithEmptyTowerData(
            KnownResultProcessor(resolutionCandidates),
            TowerResolver.SuccessfulResultCollector(),
            useOrder = true
        )
        val ca = choseMostSpecific(kotlinCall, resolutionCallbacks, candidates)
        return kotlinCallCompleter.runCompletion(candidateFactory, ca, expectedType, resolutionCallbacks)
    }

    private fun <C : KotlinResolutionCandidate> choseMostSpecific(
        kotlinCall: KotlinCall,
        resolutionCallbacks: KotlinResolutionCallbacks,
        candidates: Collection<C>
    ): Set<C> {
        var refinedCandidates = candidates
        if (!callComponents.languageVersionSettings.supportsFeature(LanguageFeature.RefinedSamAdaptersPriority) && kotlinCall.callKind != KotlinCallKind.CALLABLE_REFERENCE) {
            val nonSynthesized = candidates.filter { !it.resolvedCall.candidateDescriptor.isSynthesized }
            if (!nonSynthesized.isEmpty()) {
                refinedCandidates = nonSynthesized
            }
        }

        var maximallySpecificCandidates = if (kotlinCall.callKind == KotlinCallKind.CALLABLE_REFERENCE) {
            callableReferenceResolver.callableReferenceOverloadConflictResolver.chooseMaximallySpecificCandidates(
                refinedCandidates as Collection<CallableReferenceCandidate>,
                CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
                discriminateGenerics = true // todo
            )
        } else {
            overloadingConflictResolver.chooseMaximallySpecificCandidates(
                refinedCandidates,
                CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
                discriminateGenerics = true // todo
            )
        } as Set<C>

        if (
            maximallySpecificCandidates.size > 1 &&
            callComponents.languageVersionSettings.supportsFeature(LanguageFeature.OverloadResolutionByLambdaReturnType) &&
            candidates.all { resolutionCallbacks.inferenceSession.shouldRunCompletion(it) } &&
            kotlinCall.callKind != KotlinCallKind.CALLABLE_REFERENCE
        ) {
            val candidatesWithAnnotation =
                candidates.filter { it.resolvedCall.candidateDescriptor.annotations.hasAnnotation(OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION_FQ_NAME) }
            val candidatesWithoutAnnotation = candidates - candidatesWithAnnotation
            if (candidatesWithAnnotation.isNotEmpty()) {
                val newCandidates = kotlinCallCompleter.chooseCandidateRegardingOverloadResolutionByLambdaReturnType(maximallySpecificCandidates, resolutionCallbacks)
                maximallySpecificCandidates = overloadingConflictResolver.chooseMaximallySpecificCandidates(
                    newCandidates,
                    CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
                    discriminateGenerics = true
                ) as Set<C>

                if (maximallySpecificCandidates.size > 1 && candidatesWithoutAnnotation.any { it in maximallySpecificCandidates }) {
                    maximallySpecificCandidates = maximallySpecificCandidates.toMutableSet().apply { removeAll(candidatesWithAnnotation) }
                    maximallySpecificCandidates.singleOrNull()?.addDiagnostic(CandidateChosenUsingOverloadResolutionByLambdaAnnotation())
                }
            }
        }

        return maximallySpecificCandidates
    }

    class BaseCallContext(
        val baseSystem: NewConstraintSystem,
        val argument: CallableReferenceKotlinCallArgument,
        val diagnosticsHolder: KotlinDiagnosticsHolder
    )
}

