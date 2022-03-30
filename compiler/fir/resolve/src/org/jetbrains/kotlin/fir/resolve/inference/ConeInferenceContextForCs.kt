/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.inference

import org.jetbrains.kotlin.fir.resolve.calls.createToFreshVariableSubstitutorAndAddInitialConstraints
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterLookupTag
import org.jetbrains.kotlin.fir.types.ConeInferenceContext
import org.jetbrains.kotlin.resolve.calls.inference.model.NewConstraintSystemImpl
import org.jetbrains.kotlin.types.model.ConstraintSystemMarker
import org.jetbrains.kotlin.types.model.TypeParameterMarker
import org.jetbrains.kotlin.types.model.TypeSystemInferenceExtensionContext

class ConeInferenceContextForCs(commonContext: ConeInferenceContext) : TypeSystemInferenceExtensionContext by commonContext {
    private val session = commonContext.session

    override fun ConstraintSystemMarker.generateConstraints(parameters: List<TypeParameterMarker>) {
        @Suppress("UNCHECKED_CAST")
        parameters as List<ConeTypeParameterLookupTag>
        createToFreshVariableSubstitutorAndAddInitialConstraints(
            parameters.map { it.symbol.fir },
            this as NewConstraintSystemImpl,
            session
        )
    }
}
