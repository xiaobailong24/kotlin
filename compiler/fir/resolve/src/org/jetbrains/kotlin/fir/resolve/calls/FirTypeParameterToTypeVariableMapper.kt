/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.calls

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.inference.ConeTypeParameterBasedTypeVariable
import org.jetbrains.kotlin.fir.resolve.inference.model.ConeDeclaredUpperBoundConstraintPosition
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemOperation

fun createToFreshVariableSubstitutorAndAddInitialConstraints(
    typeParameters: List<FirTypeParameterRef>,
    csBuilder: ConstraintSystemOperation,
    session: FirSession
): Pair<ConeSubstitutor, List<ConeTypeVariable>> {
    val freshTypeVariables = typeParameters.map { ConeTypeParameterBasedTypeVariable(it.symbol) }

    val toFreshVariables = substitutorByMap(freshTypeVariables.associate { it.typeParameterSymbol to it.defaultType }, session)

    for (freshVariable in freshTypeVariables) {
        csBuilder.registerVariable(freshVariable)
    }

    fun ConeTypeParameterBasedTypeVariable.addSubtypeConstraint(
        upperBound: ConeKotlinType//,
        //position: DeclaredUpperBoundConstraintPosition
    ) {
        if ((upperBound.lowerBoundIfFlexible() as? ConeClassLikeType)?.lookupTag?.classId == StandardClassIds.Any &&
            upperBound.upperBoundIfFlexible().isMarkedNullable
        ) {
            return
        }

        csBuilder.addSubtypeConstraint(
            defaultType,
            toFreshVariables.substituteOrSelf(upperBound),
            ConeDeclaredUpperBoundConstraintPosition()
        )
    }

    for (index in typeParameters.indices) {
        val typeParameter = typeParameters[index]
        val freshVariable = freshTypeVariables[index]

        val parameterSymbolFromExpandedClass = typeParameter.symbol.fir.getTypeParameterFromExpandedClass(index, session)

        for (upperBound in parameterSymbolFromExpandedClass.symbol.resolvedBounds) {
            freshVariable.addSubtypeConstraint(upperBound.coneType/*, position*/)
        }
    }

    return toFreshVariables to freshTypeVariables
}

fun createToFreshVariableSubstitutorAndAddInitialConstraints(
    declaration: FirTypeParameterRefsOwner,
    csBuilder: ConstraintSystemOperation,
    session: FirSession
): Pair<ConeSubstitutor, List<ConeTypeVariable>> =
    createToFreshVariableSubstitutorAndAddInitialConstraints(declaration.typeParameters, csBuilder, session)

private fun FirTypeParameter.getTypeParameterFromExpandedClass(index: Int, session: FirSession): FirTypeParameter {
    val containingDeclaration = containingDeclarationSymbol.fir
    if (containingDeclaration is FirRegularClass) {
        return containingDeclaration.typeParameters.elementAtOrNull(index)?.symbol?.fir ?: this
    } else if (containingDeclaration is FirTypeAlias) {
        val typeParameterConeType = toConeType()
        val expandedConeType = containingDeclaration.expandedTypeRef.coneType
        val typeArgumentIndex = expandedConeType.typeArguments.indexOfFirst { it.type == typeParameterConeType }
        val expandedTypeFir = expandedConeType.toSymbol(session)?.fir
        if (expandedTypeFir is FirTypeParameterRefsOwner) {
            val typeParameterFir = expandedTypeFir.typeParameters.elementAtOrNull(typeArgumentIndex)?.symbol?.fir ?: return this
            if (expandedTypeFir is FirTypeAlias) {
                return typeParameterFir.getTypeParameterFromExpandedClass(typeArgumentIndex, session)
            }
            return typeParameterFir
        }
    }

    return this
}