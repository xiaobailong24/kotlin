/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinCompilationOutput
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.disambiguateName
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

private fun defaultModuleSuffix(module: KpmGradleModule, variantName: String): String =
    dashSeparatedName(variantName, module.moduleClassifier)

