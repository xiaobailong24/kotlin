/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.FragmentNameDisambiguationOmittingMain

typealias KpmJvmVariantFactory = KpmGradleFragmentFactory<GradleKpmJvmVariant>

fun KpmJvmVariantFactory(
    module: KpmGradleModule, config: KpmJvmVariantConfig = KpmJvmVariantConfig()
): KpmJvmVariantFactory = KpmJvmVariantFactory(
    KpmJvmVariantInstantiator(module, config),
    KpmJvmVariantConfigurator(config)
)

data class KpmJvmVariantConfig(
    val dependenciesConfigurationFactory: KpmFragmentDependencyConfigurationsFactory
    = KpmDefaultFragmentDependencyConfigurationsFactory,

    val compileDependencies: KotlinGradleFragmentConfigurationDefinition<GradleKpmJvmVariant>
    = DefaultKotlinCompileDependenciesDefinition,

    val runtimeDependencies: KotlinGradleFragmentConfigurationDefinition<GradleKpmJvmVariant>
    = DefaultKotlinRuntimeDependenciesDefinition,

    val apiElements: KotlinGradleFragmentConfigurationDefinition<GradleKpmJvmVariant>
    = DefaultKotlinApiElementsDefinition + KotlinFragmentCompilationOutputsJarArtifact,

    val runtimeElements: KotlinGradleFragmentConfigurationDefinition<GradleKpmJvmVariant>
    = DefaultKotlinRuntimeElementsDefinition,

    val compileTaskConfigurator: KpmCompileTaskConfigurator<GradleKpmJvmVariant>
    = KpmJvmCompileTaskConfigurator,

    val sourceArchiveTaskConfigurator: KotlinSourceArchiveTaskConfigurator<GradleKpmJvmVariant>
    = DefaultKotlinSourceArchiveTaskConfigurator,

    val sourceDirectoriesConfigurator: KpmSourceDirectoriesConfigurator<GradleKpmJvmVariant>
    = KpmDefaultSourceDirectoriesConfigurator,

    val publicationConfigurator: KotlinPublicationConfigurator<GradleKpmJvmVariant>
    = KotlinPublicationConfigurator.SingleVariantPublication
)

class KpmJvmVariantInstantiator internal constructor(
    private val module: KpmGradleModule,
    private val config: KpmJvmVariantConfig
) : KpmGradleFragmentFactory.FragmentInstantiator<GradleKpmJvmVariant> {

    override fun create(name: String): GradleKpmJvmVariant {
        val names = FragmentNameDisambiguationOmittingMain(module, name)
        val context = KotlinGradleFragmentConfigurationContextImpl(
            module, config.dependenciesConfigurationFactory.create(module, names), names
        )

        return GradleKpmJvmVariant(
            containingModule = module,
            fragmentName = name,
            dependencyConfigurations = context.dependencies,
            compileDependenciesConfiguration = config.compileDependencies.provider.getConfiguration(context).also { configuration ->
                config.compileDependencies.relations.setExtendsFrom(configuration, context)
            },
            runtimeDependenciesConfiguration = config.runtimeDependencies.provider.getConfiguration(context).also { configuration ->
                config.runtimeElements.relations.setExtendsFrom(configuration, context)
            },
            apiElementsConfiguration = config.apiElements.provider.getConfiguration(context).also { configuration ->
                config.apiElements.relations.setExtendsFrom(configuration, context)
            },
            runtimeElementsConfiguration = config.runtimeElements.provider.getConfiguration(context).also { configuration ->
                config.runtimeElements.relations.setExtendsFrom(configuration, context)
            }
        )
    }
}

class KpmJvmVariantConfigurator internal constructor(
    private val config: KpmJvmVariantConfig
) : KpmGradleFragmentFactory.FragmentConfigurator<GradleKpmJvmVariant> {

    override fun configure(fragment: GradleKpmJvmVariant) {
        fragment.compileDependenciesConfiguration.configure(config.compileDependencies, fragment)
        fragment.runtimeDependenciesConfiguration.configure(config.runtimeDependencies, fragment)
        fragment.apiElementsConfiguration.configure(config.apiElements, fragment)
        fragment.runtimeElementsConfiguration.configure(config.runtimeElements, fragment)

        config.sourceDirectoriesConfigurator.configure(fragment)
        config.compileTaskConfigurator.registerCompileTasks(fragment)
        config.sourceArchiveTaskConfigurator.registerSourceArchiveTask(fragment)
        config.publicationConfigurator.configure(fragment)
    }
}
