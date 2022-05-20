/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.FragmentNameDisambiguation

typealias KpmCommonFragmentFactory = KpmGradleFragmentFactory<GradleKpmFragmentInternal>

fun KpmCommonFragmentFactory(module: KpmGradleModule): KpmCommonFragmentFactory =
    KpmCommonFragmentFactory(KpmCommonFragmentInstantiator(module))

fun KpmCommonFragmentFactory(
    commonFragmentInstantiator: KpmCommonFragmentInstantiator,
    commonFragmentConfigurator: KpmCommonFragmentConfigurator = KpmCommonFragmentConfigurator()
): KpmGradleFragmentFactory<GradleKpmFragmentInternal> = KpmGradleFragmentFactory(
    fragmentInstantiator = commonFragmentInstantiator,
    fragmentConfigurator = commonFragmentConfigurator
)

class KpmCommonFragmentInstantiator(
    private val module: KpmGradleModule,
    private val dependencyConfigurationsFactory: KpmFragmentDependencyConfigurationsFactory =
        KpmDefaultFragmentDependencyConfigurationsFactory
) : KpmGradleFragmentFactory.FragmentInstantiator<GradleKpmFragmentInternal> {
    override fun create(name: String): GradleKpmFragmentInternal {
        val names = FragmentNameDisambiguation(module, name)
        return GradleKpmFragmentInternal(module, name, dependencyConfigurationsFactory.create(module, names))
    }
}

class KpmCommonFragmentConfigurator(
    private val sourceDirectoriesSetup: KpmSourceDirectoriesConfigurator<GradleKpmFragmentInternal> =
        KpmDefaultSourceDirectoriesConfigurator
) : KpmGradleFragmentFactory.FragmentConfigurator<GradleKpmFragmentInternal> {
    override fun configure(fragment: GradleKpmFragmentInternal) {
        sourceDirectoriesSetup.configure(fragment)
    }
}
