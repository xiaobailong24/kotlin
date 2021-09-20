/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.components.candidate

import org.jetbrains.kotlin.resolve.calls.components.ErrorDescriptorResolutionPart
import org.jetbrains.kotlin.resolve.calls.components.KotlinResolutionCallbacks
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallComponents
import org.jetbrains.kotlin.resolve.calls.model.MutableResolvedCallAtom
import org.jetbrains.kotlin.resolve.calls.model.ResolutionPart
import org.jetbrains.kotlin.resolve.calls.model.ResolvedAtom
import org.jetbrains.kotlin.resolve.calls.tower.ImplicitScopeTower
import org.jetbrains.kotlin.types.TypeSubstitutor

/**
 * baseSystem contains all information from arguments, i.e. it is union of all system of arguments
 * Also by convention we suppose that baseSystem has no contradiction
 */
open class RegularCallCandidate(
    override val callComponents: KotlinCallComponents,
    override val resolutionCallbacks: KotlinResolutionCallbacks,
    override val scopeTower: ImplicitScopeTower,
    override val baseSystem: ConstraintStorage,
    override val resolvedCall: MutableResolvedCallAtom,
    override val knownTypeParametersResultingSubstitutor: TypeSubstitutor? = null,
) : CallCandidate() {
    private var subResolvedAtoms: MutableList<ResolvedAtom> = arrayListOf()

    override val variableCandidateIfInvoke: CallCandidate?
        get() = callComponents.statelessCallbacks.getVariableCandidateIfInvoke(resolvedCall.atom)

    override fun getSubResolvedAtoms(): List<ResolvedAtom> = subResolvedAtoms

    override fun addResolvedKtPrimitive(resolvedAtom: ResolvedAtom) {
        subResolvedAtoms.add(resolvedAtom)
    }
}

class RegularErrorCallCandidate(
    callComponents: KotlinCallComponents,
    resolutionCallbacks: KotlinResolutionCallbacks,
    scopeTower: ImplicitScopeTower,
    baseSystem: ConstraintStorage,
    resolvedCall: MutableResolvedCallAtom
) : RegularCallCandidate(callComponents, resolutionCallbacks, scopeTower, baseSystem, resolvedCall) {
    override val resolutionSequence: List<ResolutionPart> = listOf(ErrorDescriptorResolutionPart)
}