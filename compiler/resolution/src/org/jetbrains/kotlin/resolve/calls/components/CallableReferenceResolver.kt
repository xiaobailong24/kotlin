/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.calls.components

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.calls.KotlinCallResolver
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.components.ConstraintInjector
import org.jetbrains.kotlin.resolve.calls.inference.components.NewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.results.*
import org.jetbrains.kotlin.resolve.calls.tower.isInapplicable
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.util.CancellationChecker


class CallableReferenceOverloadConflictResolver(
    builtIns: KotlinBuiltIns,
    module: ModuleDescriptor,
    specificityComparator: TypeSpecificityComparator,
    platformOverloadsSpecificityComparator: PlatformOverloadsSpecificityComparator,
    cancellationChecker: CancellationChecker,
    statelessCallbacks: KotlinResolutionStatelessCallbacks,
    constraintInjector: ConstraintInjector,
    kotlinTypeRefiner: KotlinTypeRefiner,
) : OverloadingConflictResolver<CallableReferenceCandidate>(
    builtIns,
    module,
    specificityComparator,
    platformOverloadsSpecificityComparator,
    cancellationChecker,
    { it.candidate },
    { statelessCallbacks.createConstraintSystemForOverloadResolution(constraintInjector, builtIns) },
    Companion::createFlatSignature,
    { null },
    { statelessCallbacks.isDescriptorFromSource(it) },
    null,
    kotlinTypeRefiner,
) {
    companion object {
        private fun createFlatSignature(candidate: CallableReferenceCandidate) =
            FlatSignature.createFromReflectionType(
                candidate, candidate.candidate, candidate.numDefaults, hasBoundExtensionReceiver = candidate.extensionReceiver != null,
                candidate.reflectionCandidateType
            )
    }
}

class CallableReferenceResolver(
    val callableReferenceOverloadConflictResolver: CallableReferenceOverloadConflictResolver,
    private val callComponents: KotlinCallComponents
) {
    fun processCallableReferenceArgument(
        baseSystem: NewConstraintSystem,
        resolvedAtom: CallableReferenceResolvedArgumentAtom,
        diagnosticsHolder: KotlinDiagnosticsHolder,
        resolutionCallbacks: KotlinResolutionCallbacks,
    ) {
        val csBuilder = baseSystem.getBuilder()
        val argument = resolvedAtom.atom
        val expectedType = resolvedAtom.expectedType?.let { (csBuilder.buildCurrentSubstitutor() as NewTypeSubstitutor).safeSubstitute(it) }

        val scopeTower = callComponents.statelessCallbacks.getScopeTowerForCallableReferenceArgument(argument)

        val candidates = resolutionCallbacks.resolveCallableReference(
            scopeTower,
            resolvedAtom.atom.kotlinCall,
            expectedType,
            KotlinCallResolver.BaseCallContext(baseSystem, resolvedAtom.atom, diagnosticsHolder)
        )

        if (candidates.size > 1 && resolvedAtom is EagerCallableReferenceAtom) {
            if (candidates.all { it.resultingApplicability.isInapplicable }) {
                diagnosticsHolder.addDiagnostic(CallableReferenceCandidatesAmbiguity(argument, candidates))
            }

            resolvedAtom.setAnalyzedResults(
                candidate = null,
                subResolvedAtoms = listOf(resolvedAtom.transformToPostponed())
            )
            return
        }

        val chosenCandidate = candidates.singleOrNull()
        if (chosenCandidate != null) {
            val sub = CreateFreshVariablesSubstitutor.createToFreshVariableSubstitutorAndAddInitialConstraints(chosenCandidate.candidate, csBuilder)
            chosenCandidate.addConstraints(csBuilder, sub, callableReference = argument)
            chosenCandidate.diagnostics.forEach {
                val transformedDiagnostic = when (it) {
                    is CompatibilityWarning -> CompatibilityWarningOnArgument(argument, it.candidate)
                    else -> it
                }
                diagnosticsHolder.addDiagnostic(transformedDiagnostic)
            }
            chosenCandidate.freshSubstitutor = sub
        } else {
            if (candidates.isEmpty()) {
                diagnosticsHolder.addDiagnostic(NoneCallableReferenceCandidates(argument))
            } else {
                diagnosticsHolder.addDiagnostic(CallableReferenceCandidatesAmbiguity(argument, candidates))
            }
        }

        // todo -- create this inside CallableReferencesCandidateFactory
        val subKtArguments = listOfNotNull(buildResolvedKtArgument(argument.lhsResult))

        resolvedAtom.setAnalyzedResults(chosenCandidate, subKtArguments)
    }

    private fun buildResolvedKtArgument(lhsResult: LHSResult): ResolvedAtom? {
        if (lhsResult !is LHSResult.Expression) return null
        val lshCallArgument = lhsResult.lshCallArgument
        return when (lshCallArgument) {
            is SubKotlinCallArgument -> lshCallArgument.callResult
            is ExpressionKotlinCallArgument -> ResolvedExpressionAtom(lshCallArgument)
            else -> unexpectedArgument(lshCallArgument)
        }
    }
}

