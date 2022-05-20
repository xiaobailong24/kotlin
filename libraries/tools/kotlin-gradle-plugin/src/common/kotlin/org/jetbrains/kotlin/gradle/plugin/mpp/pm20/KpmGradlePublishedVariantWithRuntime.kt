/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.plugin.mpp.publishedConfigurationName

abstract class KpmGradlePublishedVariantWithRuntime(
    containingModule: KpmGradleModule, fragmentName: String,
    dependencyConfigurations: KpmFragmentDependencyConfigurations,
    compileDependencyConfiguration: Configuration,
    apiElementsConfiguration: Configuration,
    runtimeDependencyConfiguration: Configuration,
    runtimeElementsConfiguration: Configuration
) : KpmGradleVariantWithRuntimeInternal(
    containingModule = containingModule,
    fragmentName = fragmentName,
    dependencyConfigurations = dependencyConfigurations,
    compileDependencyConfiguration = compileDependencyConfiguration,
    apiElementsConfiguration = apiElementsConfiguration,
    runtimeDependenciesConfiguration = runtimeDependencyConfiguration,
    runtimeElementsConfiguration = runtimeElementsConfiguration
), KpmSingleMavenPublishedModuleHolder by KpmDefaultSingleMavenPublishedModuleHolder(
    containingModule, defaultModuleSuffix(containingModule, fragmentName)
) {
    override val gradleVariantNames: Set<String>
        get() = listOf(apiElementsConfiguration.name, runtimeElementsConfiguration.name).flatMapTo(mutableSetOf()) {
            listOf(it, publishedConfigurationName(it))
        }
}
