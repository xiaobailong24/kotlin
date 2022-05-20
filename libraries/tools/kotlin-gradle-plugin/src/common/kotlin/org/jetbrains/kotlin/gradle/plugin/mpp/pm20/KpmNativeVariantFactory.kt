/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.FragmentNameDisambiguation

fun <T : KpmNativeVariantInternal> KpmNativeVariantFactory(
    module: KpmGradleModule,
    constructor: KpmNativeVariantConstructor<T>,
    config: KpmNativeVariantConfig<T> = KpmNativeVariantConfig()
) = KpmGradleFragmentFactory(
    fragmentInstantiator = KpmNativeVariantInstantiator(module, constructor, config),
    fragmentConfigurator = KpmNativeVariantConfigurator(config)
)

data class KpmNativeVariantConfig<T : KpmNativeVariantInternal>(
    val dependenciesConfigurationFactory: KpmFragmentDependencyConfigurationsFactory =
        KpmDefaultFragmentDependencyConfigurationsFactory,

    val compileDependencies: ConfigurationDefinition<T> =
        DefaultKotlinCompileDependenciesDefinition + KotlinFragmentKonanTargetAttribute,

    val apiElements: ConfigurationDefinition<T> =
        DefaultKotlinApiElementsDefinition
                + KotlinFragmentKonanTargetAttribute
                + FragmentConfigurationRelation { extendsFrom(dependencies.transitiveImplementationConfiguration) },

    val hostSpecificMetadataElements: ConfigurationDefinition<T> =
        DefaultKotlinHostSpecificMetadataElementsDefinition,

    val compileTaskConfigurator: KpmCompileTaskConfigurator<T> =
        KpmNativeCompileTaskConfigurator,

    val sourceArchiveTaskConfigurator: KotlinSourceArchiveTaskConfigurator<T> =
        DefaultKotlinSourceArchiveTaskConfigurator,

    val sourceDirectoriesConfigurator: KotlinSourceDirectoriesConfigurator<T> =
        DefaultKotlinSourceDirectoriesConfigurator,

    val publicationConfigurator: KotlinPublicationConfigurator<KpmNativeVariantInternal> =
        KotlinPublicationConfigurator.NativeVariantPublication
)

class KpmNativeVariantInstantiator<T : KpmNativeVariantInternal>(
    private val module: KpmGradleModule,
    private val variantConstructor: KpmNativeVariantConstructor<T>,
    private val config: KpmNativeVariantConfig<T>

) : KpmGradleFragmentFactory.FragmentInstantiator<T> {

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

class KpmNativeVariantConfigurator<T : KpmNativeVariantInternal>(
    private val config: KpmNativeVariantConfig<T>
) : KpmGradleFragmentFactory.FragmentConfigurator<T> {

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
