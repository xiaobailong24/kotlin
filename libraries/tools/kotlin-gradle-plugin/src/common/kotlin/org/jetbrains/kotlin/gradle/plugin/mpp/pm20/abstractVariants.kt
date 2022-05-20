/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.mpp.MavenPublicationCoordinatesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.ComputedCapability
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.disambiguateName
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.newDependencyFilesHolder
import org.jetbrains.kotlin.gradle.plugin.mpp.publishedConfigurationName
import org.jetbrains.kotlin.gradle.utils.dashSeparatedName
import org.jetbrains.kotlin.project.model.KotlinAttributeKey
import org.jetbrains.kotlin.project.model.KotlinPlatformTypeAttribute

abstract class KpmGradleVariantInternal(
    containingModule: KotlinGradleModule,
    fragmentName: String,
    dependencyConfigurations: KotlinFragmentDependencyConfigurations,
    final override val compileDependenciesConfiguration: Configuration,
    final override val apiElementsConfiguration: Configuration
) : KpmGradleFragmentInternal(
    containingModule, fragmentName, dependencyConfigurations
), KpmGradleVariant {

    override val variantAttributes: Map<KotlinAttributeKey, String>
        get() = mapOf(KotlinPlatformTypeAttribute to kotlinPlatformTypeAttributeFromPlatform(platformType)) // TODO user attributes

    override var compileDependencyFiles: FileCollection = project.files({ compileDependenciesConfiguration })

    internal abstract val compilationData: KotlinVariantCompilationDataInternal<*>

    // TODO rewrite using our own artifacts API?
    override val compilationOutputs: KotlinCompilationOutput = DefaultKotlinCompilationOutput(
        project, project.provider { project.buildDir.resolve("processedResources/${containingModule.name}/${fragmentName}") }
    )

    // TODO rewrite using our own artifacts API
    override val sourceArchiveTaskName: String
        get() = defaultSourceArtifactTaskName

    override fun toString(): String = "variant $fragmentName in $containingModule"
}

class DefaultSingleMavenPublishedModuleHolder(
    private var module: KotlinGradleModule, override val defaultPublishedModuleSuffix: String?
) : SingleMavenPublishedModuleHolder {
    private val project get() = module.project

    private var assignedMavenPublication: MavenPublication? = null

    private val publicationAssignedHandlers = mutableListOf<(MavenPublication) -> Unit>()

    override fun assignMavenPublication(publication: MavenPublication) {
        if (assignedMavenPublication != null) error("already assigned publication $publication")
        assignedMavenPublication = publication
        publicationAssignedHandlers.forEach { it(publication) }
    }

    override fun whenPublicationAssigned(handlePublication: (MavenPublication) -> Unit) {
        assignedMavenPublication?.let(handlePublication) ?: publicationAssignedHandlers.add(handlePublication)
    }

    override val publishedMavenModuleCoordinates: PublishedModuleCoordinatesProvider = MavenPublicationCoordinatesProvider(
        project,
        { assignedMavenPublication },
        defaultPublishedModuleSuffix,
        capabilities = listOfNotNull(ComputedCapability.capabilityStringFromModule(module))
    )
}

private fun kotlinPlatformTypeAttributeFromPlatform(platformType: KotlinPlatformType) = platformType.name

// TODO: rewrite with the artifacts API
internal val KpmGradleVariant.defaultSourceArtifactTaskName: String
    get() = disambiguateName("sourcesJar")

abstract class KpmGradleVariantWithRuntimeInternal(
    containingModule: KotlinGradleModule,
    fragmentName: String,
    dependencyConfigurations: KotlinFragmentDependencyConfigurations,
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

private fun defaultModuleSuffix(module: KotlinGradleModule, variantName: String): String =
    dashSeparatedName(variantName, module.moduleClassifier)

abstract class KpmGradlePublishedVariantWithRuntime(
    containingModule: KotlinGradleModule, fragmentName: String,
    dependencyConfigurations: KotlinFragmentDependencyConfigurations,
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
), SingleMavenPublishedModuleHolder by DefaultSingleMavenPublishedModuleHolder(
    containingModule, defaultModuleSuffix(containingModule, fragmentName)
) {
    override val gradleVariantNames: Set<String>
        get() = listOf(apiElementsConfiguration.name, runtimeElementsConfiguration.name).flatMapTo(mutableSetOf()) {
            listOf(it, publishedConfigurationName(it))
        }
}
