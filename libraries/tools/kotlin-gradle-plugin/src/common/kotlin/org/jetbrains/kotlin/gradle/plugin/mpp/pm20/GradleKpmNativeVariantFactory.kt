/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.FragmentNameDisambiguation

fun <T : GradleKpmNativeVariantInternal> GradleKpmNativeVariantFactory(
    module: KpmGradleModule,
    constructor: GradleKpmNativeVariantConstructor<T>,
    config: GradleKpmNativeVariantConfig<T> = GradleKpmNativeVariantConfig()
) = GradleKpmFragmentFactory(
    fragmentInstantiator = GradleKpmNativeVariantInstantiator(module, constructor, config),
    fragmentConfigurator = GradleKpmNativeVariantConfigurator(config)
)

data class GradleKpmNativeVariantConfig<T : GradleKpmNativeVariantInternal>(
    val dependenciesConfigurationFactory: GradleKpmFragmentDependencyConfigurationsFactory =
        GradleKpmDefaultFragmentDependencyConfigurationsFactory,

    val compileDependencies: ConfigurationDefinition<T> =
        DefaultKotlinCompileDependenciesDefinition + KotlinFragmentKonanTargetAttribute,

    val apiElements: ConfigurationDefinition<T> =
        DefaultKotlinApiElementsDefinition
                + KotlinFragmentKonanTargetAttribute
                + FragmentConfigurationRelation { extendsFrom(dependencies.transitiveImplementationConfiguration) },

    val hostSpecificMetadataElements: ConfigurationDefinition<T> =
        DefaultKotlinHostSpecificMetadataElementsDefinition,

    val compileTaskConfigurator: GradleKpmCompileTaskConfigurator<T> =
        GradleKpmNativeCompileTaskConfigurator,

    val sourceArchiveTaskConfigurator: GradleKpmSourceArchiveTaskConfigurator<T> =
        GradleKpmDefaultKotlinSourceArchiveTaskConfigurator,

    val sourceDirectoriesConfigurator: GradleKpmSourceDirectoriesConfigurator<T> =
        GradleKpmDefaultSourceDirectoriesConfigurator,

    val publicationConfigurator: GradleKpmPublicationConfigurator<GradleKpmNativeVariantInternal> =
        GradleKpmPublicationConfigurator.NativeVariantPublication
)

class GradleKpmNativeVariantInstantiator<T : GradleKpmNativeVariantInternal>(
    private val module: KpmGradleModule,
    private val variantConstructor: GradleKpmNativeVariantConstructor<T>,
    private val config: GradleKpmNativeVariantConfig<T>
) : GradleKpmFragmentFactory.FragmentInstantiator<T> {

    override fun create(name: String): T {
        val names = FragmentNameDisambiguation(module, name)
        val dependencies = config.dependenciesConfigurationFactory.create(module, names)
        val context = KotlinGradleFragmentConfigurationContextImpl(module, dependencies, names)

        return variantConstructor.invoke(
            containingModule = module,
            fragmentName = name,
            dependencyConfigurations = dependencies,
            compileDependencyConfiguration = config.compileDependencies.provider.getConfiguration(context).also { configuration ->
                config.compileDependencies.relations.setExtendsFrom(configuration, context)
            },
            apiElementsConfiguration = config.apiElements.provider.getConfiguration(context).also { configuration ->
                config.apiElements.relations.setExtendsFrom(configuration, context)
            },
            hostSpecificMetadataElementsConfiguration =
            config.hostSpecificMetadataElements.provider.getConfiguration(context).also { configuration ->
                config.hostSpecificMetadataElements.relations.setExtendsFrom(configuration, context)
            }
        )
    }
}

class GradleKpmNativeVariantConfigurator<T : GradleKpmNativeVariantInternal>(
    private val config: GradleKpmNativeVariantConfig<T>
) : GradleKpmFragmentFactory.FragmentConfigurator<T> {

    override fun configure(fragment: T) {
        fragment.compileDependenciesConfiguration.configure(config.compileDependencies, fragment)
        fragment.apiElementsConfiguration.configure(config.apiElements, fragment)
        fragment.hostSpecificMetadataElementsConfiguration?.configure(config.hostSpecificMetadataElements, fragment)

        config.sourceDirectoriesConfigurator.configure(fragment)
        config.compileTaskConfigurator.registerCompileTasks(fragment)
        config.sourceArchiveTaskConfigurator.registerSourceArchiveTask(fragment)
        config.publicationConfigurator.configure(fragment)
    }
}
