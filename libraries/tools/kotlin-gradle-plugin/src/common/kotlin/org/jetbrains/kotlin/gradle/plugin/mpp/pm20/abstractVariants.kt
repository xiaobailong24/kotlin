/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.disambiguateName
import org.jetbrains.kotlin.gradle.plugin.mpp.publishedConfigurationName
import org.jetbrains.kotlin.gradle.utils.dashSeparatedName
import org.jetbrains.kotlin.project.model.KotlinAttributeKey
import org.jetbrains.kotlin.project.model.KotlinPlatformTypeAttribute

abstract class KpmGradleVariantInternal(
    containingModule: KpmGradleModule,
    fragmentName: String,
    dependencyConfigurations: KpmFragmentDependencyConfigurations,
    final override val compileDependenciesConfiguration: Configuration,
    final override val apiElementsConfiguration: Configuration
) : KpmGradleFragmentInternal(
    containingModule, fragmentName, dependencyConfigurations
), KpmGradleVariant {

    override val variantAttributes: Map<KotlinAttributeKey, String>
        get() = mapOf(KotlinPlatformTypeAttribute to kotlinPlatformTypeAttributeFromPlatform(platformType)) // TODO user attributes

    override var compileDependencyFiles: FileCollection = project.files({ compileDependenciesConfiguration })

    internal abstract val compilationData: KpmVariantCompilationDataInternal<*>

    // TODO rewrite using our own artifacts API?
    override val compilationOutputs: KotlinCompilationOutput = DefaultKotlinCompilationOutput(
        project, project.provider { project.buildDir.resolve("processedResources/${containingModule.name}/${fragmentName}") }
    )

    // TODO rewrite using our own artifacts API
    override val sourceArchiveTaskName: String
        get() = defaultSourceArtifactTaskName

    override fun toString(): String = "variant $fragmentName in $containingModule"
}

private fun kotlinPlatformTypeAttributeFromPlatform(platformType: KotlinPlatformType) = platformType.name

// TODO: rewrite with the artifacts API
internal val KpmGradleVariant.defaultSourceArtifactTaskName: String
    get() = disambiguateName("sourcesJar")

abstract class KpmGradleVariantWithRuntimeInternal(
    containingModule: KpmGradleModule,
    fragmentName: String,
    dependencyConfigurations: KpmFragmentDependencyConfigurations,
    compileDependencyConfiguration: Configuration,
    apiElementsConfiguration: Configuration,
    final override val runtimeDependenciesConfiguration: Configuration,
    final override val runtimeElementsConfiguration: Configuration
) : KpmGradleVariantInternal(
    containingModule = containingModule,
    fragmentName = fragmentName,
    dependencyConfigurations = dependencyConfigurations,
    compileDependenciesConfiguration = compileDependencyConfiguration,
    apiElementsConfiguration = apiElementsConfiguration
), KpmGradleVariantWithRuntime {
    // TODO deduplicate with KotlinCompilation?

    override var runtimeDependencyFiles: FileCollection = project.files(runtimeDependenciesConfiguration)

    override val runtimeFiles: ConfigurableFileCollection =
        project.files(listOf({ compilationOutputs.allOutputs }, { runtimeDependencyFiles }))
}

private fun defaultModuleSuffix(module: KpmGradleModule, variantName: String): String =
    dashSeparatedName(variantName, module.moduleClassifier)

abstract class KpmGradlePublishedVariantWithRuntimeKpm(
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
