/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.FragmentNameDisambiguationOmittingMain

typealias KotlinJvmVariantFactory = KpmGradleFragmentFactory<KpmJvmVariant>

fun KotlinJvmVariantFactory(
    module: KpmGradleModule, config: KotlinJvmVariantConfig = KotlinJvmVariantConfig()
): KotlinJvmVariantFactory = KotlinJvmVariantFactory(
    KotlinJvmVariantInstantiator(module, config),
    KotlinJvmVariantConfigurator(config)
)

data class KotlinJvmVariantConfig(
    val dependenciesConfigurationFactory: KpmFragmentDependencyConfigurationsFactory
    = KpmDefaultFragmentDependencyConfigurationsFactory,

    val compileDependencies: KotlinGradleFragmentConfigurationDefinition<KpmJvmVariant>
    = DefaultKotlinCompileDependenciesDefinition,

    val runtimeDependencies: KotlinGradleFragmentConfigurationDefinition<KpmJvmVariant>
    = DefaultKotlinRuntimeDependenciesDefinition,

    val apiElements: KotlinGradleFragmentConfigurationDefinition<KpmJvmVariant>
    = DefaultKotlinApiElementsDefinition + KotlinFragmentCompilationOutputsJarArtifact,

    val runtimeElements: KotlinGradleFragmentConfigurationDefinition<KpmJvmVariant>
    = DefaultKotlinRuntimeElementsDefinition,

    val compileTaskConfigurator: KpmCompileTaskConfigurator<KpmJvmVariant>
    = KpmJvmCompileTaskConfigurator,

    val sourceArchiveTaskConfigurator: KotlinSourceArchiveTaskConfigurator<KpmJvmVariant>
    = DefaultKotlinSourceArchiveTaskConfigurator,

    val sourceDirectoriesConfigurator: KotlinSourceDirectoriesConfigurator<KpmJvmVariant>
    = DefaultKotlinSourceDirectoriesConfigurator,

    val publicationConfigurator: KotlinPublicationConfigurator<KpmJvmVariant>
    = KotlinPublicationConfigurator.SingleVariantPublication
)

class KotlinJvmVariantInstantiator internal constructor(
    private val module: KpmGradleModule,
    private val config: KotlinJvmVariantConfig
) : KpmGradleFragmentFactory.FragmentInstantiator<KpmJvmVariant> {

    override fun create(name: String): KpmJvmVariant {
        val names = FragmentNameDisambiguationOmittingMain(module, name)
        val context = KotlinGradleFragmentConfigurationContextImpl(
            module, config.dependenciesConfigurationFactory.create(module, names), names
        )

        return KpmJvmVariant(
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

class KotlinJvmVariantConfigurator internal constructor(
    private val config: KotlinJvmVariantConfig
) : KpmGradleFragmentFactory.FragmentConfigurator<KpmJvmVariant> {

    override fun configure(fragment: KpmJvmVariant) {
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
