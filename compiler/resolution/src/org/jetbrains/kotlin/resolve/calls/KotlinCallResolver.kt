/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.resolve.calls.components.*
import org.jetbrains.kotlin.resolve.calls.components.candidate.CallCandidate
import org.jetbrains.kotlin.resolve.calls.components.candidate.CallableReferenceCallCandidate
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
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
    private val callableReferenceArgumentResolver: CallableReferenceArgumentResolver,
    private val callComponents: KotlinCallComponents
) {
    fun resolveCallableReference(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        factory: CandidateFactory<CallCandidate>
    ): Set<CallableReferenceCallCandidate> {
        val processor = createCallableReferenceProcessor(factory as CallableReferencesCandidateFactory)
        val candidates = towerResolver.runResolve(scopeTower, processor, useOrder = true, name = kotlinCall.name)

        return callableReferenceArgumentResolver.callableReferenceOverloadConflictResolver.chooseMaximallySpecificCandidates(
            candidates,
            CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
            discriminateGenerics = false // we can't specify generics explicitly for callable references
        )
    }

    private fun createCallableReferenceFactory(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        resolutionCallbacks: KotlinResolutionCallbacks,
        expectedType: UnwrappedType?,
        argument: CallableReferenceKotlinCallArgument? = null,
        baseSystem: ConstraintStorage? = null
    ): CandidateFactory<CallableReferenceCallCandidate> {
        val resolutionAtom = argument
            ?: CallableReferenceKotlinCall(kotlinCall, resolutionCallbacks.getLhsResult(kotlinCall), kotlinCall.name)

        return CallableReferencesCandidateFactory(resolutionAtom, callComponents, scopeTower, expectedType, baseSystem, resolutionCallbacks)
    }

    private fun createRegularCallFactory(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        resolutionCallbacks: KotlinResolutionCallbacks,
    ): CandidateFactory<CallCandidate> = SimpleCandidateFactory(callComponents, scopeTower, kotlinCall, resolutionCallbacks)

    private fun createFactory(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        resolutionCallbacks: KotlinResolutionCallbacks,
        expectedType: UnwrappedType?
    ): CandidateFactory<CallCandidate> =
        when (kotlinCall.callKind) {
            KotlinCallKind.CALLABLE_REFERENCE -> createCallableReferenceFactory(scopeTower, kotlinCall, resolutionCallbacks, expectedType)
            else -> createRegularCallFactory(scopeTower, kotlinCall, resolutionCallbacks)
        }

    fun resolveAndCompleteCall(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        collectAllCandidates: Boolean,
    ): CallResolutionResult {
        val candidateFactory = createFactory(scopeTower, kotlinCall, resolutionCallbacks, expectedType)
        val candidates = resolveCall(scopeTower, resolutionCallbacks, kotlinCall, collectAllCandidates, candidateFactory)

        if (collectAllCandidates) {
            return kotlinCallCompleter.createAllCandidatesResult(candidates, expectedType, resolutionCallbacks)
        }

        return kotlinCallCompleter.runCompletion(candidateFactory, candidates, expectedType, resolutionCallbacks)
    }

    fun resolveCallableReferenceArgument(
        argument: CallableReferenceKotlinCallArgument,
        expectedType: UnwrappedType?,
        baseSystem: ConstraintStorage,
        resolutionCallbacks: KotlinResolutionCallbacks
    ): Collection<CallableReferenceCallCandidate> {
        val scopeTower = callComponents.statelessCallbacks.getScopeTowerForCallableReferenceArgument(argument)
        val factory = createCallableReferenceFactory(scopeTower, argument.call, resolutionCallbacks, expectedType, argument, baseSystem)

        return resolveCall(scopeTower, resolutionCallbacks, argument.call, collectAllCandidates = false, factory)
    }

    private fun <C : CallCandidate> resolveCall(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        collectAllCandidates: Boolean,
        candidateFactory: CandidateFactory<C>,
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
            return towerResolver.collectAllCandidates(scopeTower, processor, kotlinCall.name)
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
        val candidateFactory = SimpleCandidateFactory(callComponents, scopeTower, kotlinCall, resolutionCallbacks)

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

    private fun <C : CallCandidate> choseMostSpecific(
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
            callableReferenceArgumentResolver.callableReferenceOverloadConflictResolver.chooseMaximallySpecificCandidates(
                refinedCandidates as Collection<CallableReferenceCallCandidate>,
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

    class ArgumentContext(
        val baseSystem: ConstraintStorage,
        val argument: CallableReferenceKotlinCallArgument,
    )
}

