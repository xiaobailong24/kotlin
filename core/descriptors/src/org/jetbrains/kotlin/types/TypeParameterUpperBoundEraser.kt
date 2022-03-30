/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.TypeUtils.makeStarProjection
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.error.ErrorTypeKind
import org.jetbrains.kotlin.types.typeUtil.extractTypeParametersFromUpperBounds
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjectionOrMapped
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

class TypeParameterUpperBoundEraser(val projectionComputer: ErasureProjectionComputer) {
    private val storage = LockBasedStorageManager("Type parameter upper bound erasure results")
    private val erroneousErasedBound by lazy {
        ErrorUtils.createErrorType(ErrorTypeKind.CANNOT_COMPUTE_ERASED_BOUND, this.toString())
    }

    private data class DataToEraseUpperBound(
        val typeParameter: TypeParameterDescriptor,
        val typeAttr: ErasureTypeAttributes
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is DataToEraseUpperBound) return false
            return other.typeParameter == this.typeParameter && other.typeAttr == this.typeAttr
        }

        override fun hashCode(): Int {
            var result = typeParameter.hashCode()
            result += 31 * result + typeAttr.hashCode()
            return result
        }
    }

    private val getErasedUpperBound = storage.createMemoizedFunction<DataToEraseUpperBound, KotlinType> {
        with(it) { getErasedUpperBoundInternal(typeParameter, typeAttr) }
    }

    fun getErasedUpperBound(
        typeParameter: TypeParameterDescriptor,
        typeAttr: ErasureTypeAttributes
    ) = getErasedUpperBound(DataToEraseUpperBound(typeParameter, typeAttr))

    private fun getDefaultType(typeAttr: ErasureTypeAttributes) =
        typeAttr.defaultType?.replaceArgumentsWithStarProjections() ?: erroneousErasedBound

    // Definition:
    // ErasedUpperBound(T : G<t>) = G<*> // UpperBound(T) is a type G<t> with arguments
    // ErasedUpperBound(T : A) = A // UpperBound(T) is a type A without arguments
    // ErasedUpperBound(T : F) = UpperBound(F) // UB(T) is another type parameter F
    private fun getErasedUpperBoundInternal(
        // Calculation of `potentiallyRecursiveTypeParameter.upperBounds` may recursively depend on `this.getErasedUpperBound`
        // E.g. `class A<T extends A, F extends A>`
        // To prevent recursive calls return defaultValue() instead
        typeParameter: TypeParameterDescriptor,
        typeAttr: ErasureTypeAttributes
    ): KotlinType {
        val visitedTypeParameters = typeAttr.visitedTypeParameters

        if (visitedTypeParameters != null && typeParameter.original in visitedTypeParameters)
            return getDefaultType(typeAttr)

        /*
         * We should do erasure of containing type parameters with their erasure to avoid creating inconsistent types.
         * E.g. for `class Foo<T: Foo<B>, B>`, we'd have erasure for lower bound: Foo<Foo<*>, Any>,
         * but it's wrong type: projection(*) != projection(Any).
         * So we should substitute erasure of the corresponding type parameter: `Foo<Foo<Any>, Any>` or `Foo<Foo<*>, *>`.
         */
        val erasedUpperBounds = typeParameter.defaultType.extractTypeParametersFromUpperBounds(visitedTypeParameters).associate {
            val boundProjection = if (visitedTypeParameters == null || it !in visitedTypeParameters) {
                projectionComputer.computeProjection(
                    it,
                    typeAttr,
                    this,
                    getErasedUpperBound(it, typeAttr.withNewVisitedTypeParameter(typeParameter))
                )
            } else makeStarProjection(it, typeAttr)

            it.typeConstructor to boundProjection
        }
        val erasedUpperBoundsSubstitutor = TypeSubstitutor.create(TypeConstructorSubstitution.createByConstructorsMap(erasedUpperBounds))

        val firstUpperBound = typeParameter.upperBounds.first()

        if (firstUpperBound.constructor.declarationDescriptor is ClassDescriptor) {
            return firstUpperBound.replaceArgumentsWithStarProjectionOrMapped(
                erasedUpperBoundsSubstitutor,
                erasedUpperBounds,
                Variance.OUT_VARIANCE,
                typeAttr.visitedTypeParameters
            )
        }

        val stopAt = typeAttr.visitedTypeParameters ?: setOf(this)
        var current = firstUpperBound.constructor.declarationDescriptor as TypeParameterDescriptor

        while (current !in stopAt) {
            val nextUpperBound = current.upperBounds.first()
            if (nextUpperBound.constructor.declarationDescriptor is ClassDescriptor) {
                return nextUpperBound.replaceArgumentsWithStarProjectionOrMapped(
                    erasedUpperBoundsSubstitutor,
                    erasedUpperBounds,
                    Variance.OUT_VARIANCE,
                    typeAttr.visitedTypeParameters
                )
            }

            current = nextUpperBound.constructor.declarationDescriptor as TypeParameterDescriptor
        }

        return getDefaultType(typeAttr)
    }
}