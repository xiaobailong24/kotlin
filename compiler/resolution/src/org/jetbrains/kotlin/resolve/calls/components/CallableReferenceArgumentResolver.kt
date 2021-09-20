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

import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.components.NewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.isInapplicable

class CallableReferenceArgumentResolver(val callableReferenceOverloadConflictResolver: CallableReferenceOverloadConflictResolver) {
    fun processCallableReferenceArgument(
        baseSystem: NewConstraintSystem,
        resolvedAtom: ResolvedCallableReferenceArgumentAtom,
        diagnosticsHolder: KotlinDiagnosticsHolder,
        resolutionCallbacks: KotlinResolutionCallbacks
    ) {
        val csBuilder = baseSystem.getBuilder()
        val argument = resolvedAtom.atom
        val expectedType = resolvedAtom.expectedType?.let { (csBuilder.buildCurrentSubstitutor() as NewTypeSubstitutor).safeSubstitute(it) }
        val candidates = resolutionCallbacks.resolveCallableReferenceArgument(resolvedAtom.atom, expectedType, csBuilder.currentStorage())

        if (candidates.size > 1 && resolvedAtom is EagerCallableReferenceAtom) {
            if (candidates.all { it.resultingApplicability.isInapplicable }) {
                diagnosticsHolder.addDiagnostic(CallableReferenceCallCandidatesAmbiguity(argument, candidates))
            }

            resolvedAtom.setAnalyzedResults(
                candidate = null,
                subResolvedAtoms = listOf(resolvedAtom.transformToPostponed())
            )
            return
        }

        val chosenCandidate = candidates.singleOrNull()
        if (chosenCandidate != null) {
            val toFreshSubstitutor = CreateFreshVariablesSubstitutor.createToFreshVariableSubstitutorAndAddInitialConstraints(
                chosenCandidate.candidate,
                csBuilder
            )
            chosenCandidate.addConstraints(csBuilder, toFreshSubstitutor, callableReference = argument)
            chosenCandidate.diagnostics.forEach {
                val transformedDiagnostic = when (it) {
                    is CompatibilityWarning -> CompatibilityWarningOnArgument(argument, it.candidate)
                    else -> it
                }
                diagnosticsHolder.addDiagnostic(transformedDiagnostic)
            }
            chosenCandidate.freshVariablesSubstitutor = toFreshSubstitutor
        } else {
            if (candidates.isEmpty()) {
                diagnosticsHolder.addDiagnostic(NoneCallableReferenceCallCandidates(argument))
            } else {
                diagnosticsHolder.addDiagnostic(CallableReferenceCallCandidatesAmbiguity(argument, candidates))
            }
        }

        // todo -- create this inside CallableReferencesCandidateFactory
        val subKtArguments = listOfNotNull(buildResolvedKtArgument(argument.lhsResult))

        resolvedAtom.setAnalyzedResults(chosenCandidate, subKtArguments)
    }

    private fun buildResolvedKtArgument(lhsResult: LHSResult): ResolvedAtom? {
        if (lhsResult !is LHSResult.Expression) return null
        return when (val lshCallArgument = lhsResult.lshCallArgument) {
            is SubKotlinCallArgument -> lshCallArgument.callResult
            is ExpressionKotlinCallArgument -> ResolvedExpressionAtom(lshCallArgument)
            else -> unexpectedArgument(lshCallArgument)
        }
    }
}

