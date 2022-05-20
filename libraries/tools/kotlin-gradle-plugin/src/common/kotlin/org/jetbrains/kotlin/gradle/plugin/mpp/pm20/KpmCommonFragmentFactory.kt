/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.FragmentNameDisambiguation

typealias KpmCommonFragmentFactory = KpmGradleFragmentFactory<KpmGradleFragmentInternal>

fun KpmCommonFragmentFactory(module: KpmGradleModule): KpmCommonFragmentFactory =
    KpmCommonFragmentFactory(KpmCommonFragmentInstantiator(module))

fun KpmCommonFragmentFactory(
    commonFragmentInstantiator: KpmCommonFragmentInstantiator,
    commonFragmentConfigurator: KpmCommonFragmentConfigurator = KpmCommonFragmentConfigurator()
): KpmGradleFragmentFactory<KpmGradleFragmentInternal> = KpmGradleFragmentFactory(
    fragmentInstantiator = commonFragmentInstantiator,
    fragmentConfigurator = commonFragmentConfigurator
)

class KpmCommonFragmentInstantiator(
    private val module: KpmGradleModule,
    private val dependencyConfigurationsFactory: KpmFragmentDependencyConfigurationsFactory =
        KpmDefaultFragmentDependencyConfigurationsFactory
) : KpmGradleFragmentFactory.FragmentInstantiator<KpmGradleFragmentInternal> {
    override fun create(name: String): KpmGradleFragmentInternal {
        val names = FragmentNameDisambiguation(module, name)
        return KpmGradleFragmentInternal(module, name, dependencyConfigurationsFactory.create(module, names))
    }
}

class KpmCommonFragmentConfigurator(
    private val sourceDirectoriesSetup: KotlinSourceDirectoriesConfigurator<KpmGradleFragmentInternal> =
        DefaultKotlinSourceDirectoriesConfigurator
) : KpmGradleFragmentFactory.FragmentConfigurator<KpmGradleFragmentInternal> {
    override fun configure(fragment: KpmGradleFragmentInternal) {
        sourceDirectoriesSetup.configure(fragment)
    }
}
