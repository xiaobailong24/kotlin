/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

internal class KpmFragmentGranularMetadataResolverFactory {
    private val resolvers = mutableMapOf<GradleKpmFragment, KpmFragmentGranularMetadataResolver>()

    fun getOrCreate(fragment: GradleKpmFragment): KpmFragmentGranularMetadataResolver = resolvers.getOrPut(fragment) {
        KpmFragmentGranularMetadataResolver(fragment, lazy {
            fragment.refinesClosure.map { refinesFragment ->
                getOrCreate(refinesFragment)
            }
        })
    }
}
