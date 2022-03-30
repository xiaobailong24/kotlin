/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.EmptyIntersectionTypeKind
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.model.PostponedAtomWithRevisableExpectedType
import org.jetbrains.kotlin.resolve.calls.model.PostponedResolvedAtomMarker
import org.jetbrains.kotlin.types.AbstractTypeChecker.isRelatedBySubtypingTo
import org.jetbrains.kotlin.types.model.*

abstract class ConstraintSystemCompletionContext : VariableFixationFinder.Context, ResultTypeResolver.Context {
    abstract val allTypeVariables: Map<TypeConstructorMarker, TypeVariableMarker>
    abstract override val notFixedTypeVariables: Map<TypeConstructorMarker, VariableWithConstraints>
    abstract override val fixedTypeVariables: Map<TypeConstructorMarker, KotlinTypeMarker>
    abstract override val postponedTypeVariables: List<TypeVariableMarker>

    abstract fun getBuilder(): ConstraintSystemBuilder

    // type can be proper if it not contains not fixed type variables
    abstract fun canBeProper(type: KotlinTypeMarker): Boolean

    abstract fun containsOnlyFixedOrPostponedVariables(type: KotlinTypeMarker): Boolean
    abstract fun containsOnlyFixedVariables(type: KotlinTypeMarker): Boolean

    // mutable operations
    abstract fun addError(error: ConstraintSystemError)

    abstract fun fixVariable(variable: TypeVariableMarker, resultType: KotlinTypeMarker, position: FixVariableConstraintPosition<*>)

    abstract fun couldBeResolvedWithUnrestrictedBuilderInference(): Boolean
    abstract fun processForkConstraints()

    private fun TypeConstructorMarker.isDefinitelyClass() = isClassTypeConstructor() && !isInterface()

    fun Collection<KotlinTypeMarker>.determineEmptyIntersectionTypeKind(
        constraintSystem: NewConstraintSystem?
    ): EmptyIntersectionTypeKind {
        val indexedComponents = withIndex()

        for ((i, first) in indexedComponents) {
            val firstTypeConstructor = first.typeConstructor()
            for ((j, second) in indexedComponents) {
                if (i >= j) continue

                val secondTypeConstructor = second.typeConstructor()

                if (!firstTypeConstructor.isDefinitelyClass() && !firstTypeConstructor.isTypeParameterTypeConstructor())
                    continue
                if (!secondTypeConstructor.isDefinitelyClass() && !secondTypeConstructor.isTypeParameterTypeConstructor())
                    continue

                // constraintSystem == null means the intersection type doesn't contain type parameters
                if (constraintSystem == null) {
                    if (isRelatedBySubtypingTo(this@ConstraintSystemCompletionContext, first, second)) {
                        continue
                    } else {
                        return EmptyIntersectionTypeKind.MULTIPLE_CLASSES
                    }
                }

                val completerContext = constraintSystem.asConstraintSystemCompleterContext()
                val substitutionMap = completerContext.allTypeVariables.entries.associate { (key, value) ->
                    val typeParameter = (key as TypeVariableTypeConstructorMarker).typeParameter
                    require(typeParameter != null) {
                        "Constraint system for checking type parameters for intersection emptiness" +
                                "should be build with type variables which refer to those type parameters"
                    }
                    typeParameter.getTypeConstructor() to value.defaultType()
                }
                val substitutor = typeSubstitutorByTypeConstructor(substitutionMap)

                var firstContr = false
                var secondContr = false
                completerContext.getBuilder().runTransaction {
                    addSubtypeConstraint(
                        substitutor.safeSubstitute(first), substitutor.safeSubstitute(second), TypeParametersIntersectionEmptyCheckingPosition
                    )
                    if (hasContradiction) {
                        firstContr = true
                        return@runTransaction false
                    }
                    val typeVariables = completerContext.getBuilder().currentStorage().notFixedTypeVariables.values

                    for (typeVariable in typeVariables) {
                        with ((constraintSystem as NewConstraintSystemImpl).utilContext) {
                            val r = typeVariable.findResultType(completerContext)
                            if (r.typeConstructor().isIntersection()) {
                                val a = r.typeConstructor().supertypes().determineEmptyIntersectionTypeKind(constraintSystem)
                                if (a == EmptyIntersectionTypeKind.MULTIPLE_CLASSES) {
                                    firstContr = true
                                    return@runTransaction false
                                }
                            }
                        }
                    }

                    false
                }
                completerContext.getBuilder().runTransaction {
                    addSubtypeConstraint(
                        substitutor.safeSubstitute(second), substitutor.safeSubstitute(first), TypeParametersIntersectionEmptyCheckingPosition
                    )
                    if (hasContradiction) {
                        secondContr = true
                        return@runTransaction false
                    }
                    val typeVariables = completerContext.getBuilder().currentStorage().notFixedTypeVariables.values

                    for (typeVariable in typeVariables) {
                        with ((constraintSystem as NewConstraintSystemImpl).utilContext) {
                            // check in proper order, it seems we need to really fix variables
                            val r = typeVariable.findResultType(completerContext)
                            if (r.typeConstructor().isIntersection()) {
                                val a = r.typeConstructor().supertypes().determineEmptyIntersectionTypeKind(constraintSystem)
                                if (a == EmptyIntersectionTypeKind.MULTIPLE_CLASSES) {
                                    firstContr = true
                                    return@runTransaction false
                                }
                            }
                        }
                    }
                    false
                }


                if (firstContr && secondContr) {
                    return EmptyIntersectionTypeKind.MULTIPLE_CLASSES
                }
            }
        }
        return EmptyIntersectionTypeKind.NOT_EMPTY_INTERSECTION
    }

    fun <A : PostponedResolvedAtomMarker> analyzeArgumentWithFixedParameterTypes(
        languageVersionSettings: LanguageVersionSettings,
        postponedArguments: List<A>,
        analyze: (A) -> Unit
    ): Boolean {
        val useBuilderInferenceOnlyIfNeeded =
            languageVersionSettings.supportsFeature(LanguageFeature.UseBuilderInferenceOnlyIfNeeded)
        val argumentToAnalyze = if (useBuilderInferenceOnlyIfNeeded) {
            findPostponedArgumentWithFixedInputTypes(postponedArguments)
        } else {
            findPostponedArgumentWithFixedOrPostponedInputTypes(postponedArguments)
        }

        if (argumentToAnalyze != null) {
            analyze(argumentToAnalyze)
            return true
        }

        return false
    }

    fun <A : PostponedResolvedAtomMarker> analyzeNextReadyPostponedArgument(
        languageVersionSettings: LanguageVersionSettings,
        postponedArguments: List<A>,
        completionMode: ConstraintSystemCompletionMode,
        analyze: (A) -> Unit
    ): Boolean {
        if (completionMode == ConstraintSystemCompletionMode.FULL) {
            val argumentWithTypeVariableAsExpectedType = findPostponedArgumentWithRevisableExpectedType(postponedArguments)

            if (argumentWithTypeVariableAsExpectedType != null) {
                analyze(argumentWithTypeVariableAsExpectedType)
                return true
            }
        }

        return analyzeArgumentWithFixedParameterTypes(languageVersionSettings, postponedArguments, analyze)
    }

    fun <A : PostponedResolvedAtomMarker> analyzeRemainingNotAnalyzedPostponedArgument(
        postponedArguments: List<A>,
        analyze: (A) -> Unit
    ): Boolean {
        val remainingNotAnalyzedPostponedArgument = postponedArguments.firstOrNull { !it.analyzed }

        if (remainingNotAnalyzedPostponedArgument != null) {
            analyze(remainingNotAnalyzedPostponedArgument)
            return true
        }

        return false
    }

    fun <A : PostponedResolvedAtomMarker> hasLambdaToAnalyze(
        languageVersionSettings: LanguageVersionSettings,
        postponedArguments: List<A>
    ): Boolean {
        return analyzeArgumentWithFixedParameterTypes(languageVersionSettings, postponedArguments) {}
    }

    // Avoiding smart cast from filterIsInstanceOrNull looks dirty
    private fun <A : PostponedResolvedAtomMarker> findPostponedArgumentWithRevisableExpectedType(postponedArguments: List<A>): A? =
        postponedArguments.firstOrNull { argument -> argument is PostponedAtomWithRevisableExpectedType }

    private fun <T : PostponedResolvedAtomMarker> findPostponedArgumentWithFixedOrPostponedInputTypes(
        postponedArguments: List<T>
    ) = postponedArguments.firstOrNull { argument -> argument.inputTypes.all { containsOnlyFixedOrPostponedVariables(it) } }

    private fun <T : PostponedResolvedAtomMarker> findPostponedArgumentWithFixedInputTypes(
        postponedArguments: List<T>
    ) = postponedArguments.firstOrNull { argument -> argument.inputTypes.all { containsOnlyFixedVariables(it) } }

    fun List<Constraint>.extractUpperTypes(): List<KotlinTypeMarker> =
        filter { constraint ->
            constraint.kind == ConstraintKind.UPPER && !constraint.type.contains {
                !it.typeConstructor().isClassTypeConstructor() && !it.typeConstructor().isTypeParameterTypeConstructor()
            }
        }.map { it.type }

    fun Collection<VariableWithConstraints>.extractTypeVariableUpperTypeInfo():
            Map<TypeVariableMarker, Pair<List<KotlinTypeMarker>, List<TypeParameterMarker>>> =
        associate { variableWithConstraints ->
            val upperTypes = variableWithConstraints.constraints.extractUpperTypes().map { it.withNullability(false) }
            val involvedTypeParameters = upperTypes.extractAllDependantTypeParameters()

            variableWithConstraints.typeVariable to (upperTypes to involvedTypeParameters)
        }.filter { it.value.first.isNotEmpty() }

    fun List<KotlinTypeMarker>.extractAllDependantTypeParameters(): List<TypeParameterMarker> =
        map { it.extractAllDependantTypeParameters().toList() }.flatten()
}
