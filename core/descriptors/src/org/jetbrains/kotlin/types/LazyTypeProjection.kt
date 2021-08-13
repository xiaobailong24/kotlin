/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.types.refinement.TypeRefinement

class LazyTypeProjection(
    storageManager: StorageManager,
    computation: () -> TypeProjection,
) : TypeProjectionBase() {
    private val lazyValue = storageManager.createLazyValue(computation)
    private val delegate: TypeProjection get() = lazyValue()

    override fun getProjectionKind(): Variance = delegate.projectionKind
    override fun getType(): KotlinType = delegate.type
    override fun isStarProjection(): Boolean = delegate.isStarProjection

    @TypeRefinement
    override fun refine(kotlinTypeRefiner: KotlinTypeRefiner): TypeProjection = delegate.refine(kotlinTypeRefiner)

    override fun replaceType(type: KotlinType): TypeProjection = delegate.replaceType(type)
}