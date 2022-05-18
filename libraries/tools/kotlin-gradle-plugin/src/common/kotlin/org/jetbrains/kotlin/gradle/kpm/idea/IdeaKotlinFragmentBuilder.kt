/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.kpm.idea

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.gradle.kpm.KotlinExternalModelContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.*

internal fun IdeaKotlinProjectModelBuildingContext.IdeaKotlinFragment(fragment: KotlinGradleFragment): IdeaKotlinFragment {
    return if (fragment is KotlinGradleVariant) buildIdeaKotlinVariant(fragment)
    else buildIdeaKotlinFragment(fragment)
}

private fun IdeaKotlinProjectModelBuildingContext.buildIdeaKotlinFragment(fragment: KotlinGradleFragment): IdeaKotlinFragment {
    val compilerArguments = IdeaKotlinCompilerArgumentsResolver.resolve(fragment)
    return IdeaKotlinFragmentImpl(
        coordinates = IdeaKotlinFragmentCoordinates(fragment),
        platforms = fragment.containingVariants.map { variant -> IdeaKotlinPlatform(variant) }.toSet(),
        languageSettings = IdeaKotlinLanguageSettings(fragment.languageSettings),
        dependencies = dependencyResolver.resolve(fragment).toList(),
        sourceDirectories = fragment.kotlinSourceRoots.sourceDirectories.files.toList().map { file -> IdeaKotlinSourceDirectoryImpl(file) },
        resourceDirectories = emptyList(),
        external = (fragment as? KotlinGradleFragmentInternal)?.external ?: KotlinExternalModelContainer.Empty,
        languageFeatures = compilerArguments?.configureLanguageFeatures(MessageCollector.NONE)?.entries.orEmpty()
            .associate { (feature, state) -> feature.name to state.name },
        analysisFlags = compilerArguments?.let { args ->
            args.configureAnalysisFlags(MessageCollector.NONE, args.toLanguageVersionSettings(MessageCollector.NONE).languageVersion)
                .mapKeys { (flag, value) ->
                    flag.toString()
                }
        }.orEmpty()
    )
}

private fun IdeaKotlinProjectModelBuildingContext.buildIdeaKotlinVariant(variant: KotlinGradleVariant): IdeaKotlinVariant {
    return IdeaKotlinVariantImpl(
        fragment = buildIdeaKotlinFragment(variant),
        platform = IdeaKotlinPlatform(variant),
        variantAttributes = variant.variantAttributes.mapKeys { (key, _) -> key.uniqueName },
        compilationOutputs = IdeaKotlinCompilationOutput(variant.compilationOutputs)
    )
}
