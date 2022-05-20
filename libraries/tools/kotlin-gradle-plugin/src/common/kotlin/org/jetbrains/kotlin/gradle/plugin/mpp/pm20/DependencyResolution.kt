/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.*
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.capabilities.Capability
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultLanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.utils.getOrPutRootProjectProperty
import org.jetbrains.kotlin.project.model.*
import org.jetbrains.kotlin.project.model.KpmVariant
import java.util.*

class KpmCachingModuleDependencyResolver(private val actualResolver: KpmModuleDependencyResolver) : KpmModuleDependencyResolver {
    private val cacheByRequestingModule = WeakHashMap<KpmModule, MutableMap<KpmModuleDependency, KpmModule?>>()

    private fun cacheForRequestingModule(requestingModule: KpmModule) =
        cacheByRequestingModule.getOrPut(requestingModule) { mutableMapOf() }

    override fun resolveDependency(requestingModule: KpmModule, moduleDependency: KpmModuleDependency): KpmModule? =
        cacheForRequestingModule(requestingModule).getOrPut(moduleDependency) {
            actualResolver.resolveDependency(requestingModule, moduleDependency)
        }
}

open class GradleComponentResultCachingResolver {
    private val cachedResultsByRequestingModule = mutableMapOf<KpmGradleModule, Map<KpmModuleIdentifier, ResolvedComponentResult>>()

    protected open fun configurationToResolve(requestingModule: KpmGradleModule): Configuration =
        configurationToResolveMetadataDependencies(requestingModule.project, requestingModule)

    protected open fun resolveDependencies(module: KpmGradleModule): Map<KpmModuleIdentifier, ResolvedComponentResult> {
        val allComponents = configurationToResolve(module).incoming.resolutionResult.allComponents
        // FIXME handle multi-component results
        return allComponents.flatMap { component -> component.toModuleIdentifiers().map { it to component } }.toMap()
    }

    private fun getResultsForModule(module: KpmGradleModule): Map<KpmModuleIdentifier, ResolvedComponentResult> =
        cachedResultsByRequestingModule.getOrPut(module) { resolveDependencies(module) }

    fun resolveModuleDependencyAsComponentResult(
        requestingModule: KpmGradleModule,
        moduleDependency: KpmModuleDependency
    ): ResolvedComponentResult? =
        getResultsForModule(requestingModule)[moduleDependency.moduleIdentifier]

    companion object {
        fun getForCurrentBuild(project: Project): GradleComponentResultCachingResolver {
            val extraPropertyName = "org.jetbrains.kotlin.dependencyResolution.gradleComponentResolver.${project.getKotlinPluginVersion()}"
            return project.getOrPutRootProjectProperty(extraPropertyName) {
                GradleComponentResultCachingResolver()
            }
        }
    }
}

class KpmGradleModuleDependencyResolver(
    private val gradleComponentResultResolver: GradleComponentResultCachingResolver,
    private val projectStructureMetadataModuleBuilder: ProjectStructureMetadataModuleBuilder,
    private val projectModuleBuilder: GradleProjectModuleBuilder
) : KpmModuleDependencyResolver {

    override fun resolveDependency(requestingModule: KpmModule, moduleDependency: KpmModuleDependency): KpmModule? {
        require(requestingModule is KpmGradleModule)
        val project = requestingModule.project

        val component = gradleComponentResultResolver.resolveModuleDependencyAsComponentResult(requestingModule, moduleDependency)
        val id = component?.id

        //FIXME multiple?
        val classifier = moduleClassifiersFromCapabilities(component?.variants?.flatMap { it.capabilities }.orEmpty()).single()

        return when {
            id is ProjectComponentIdentifier && id.build.isCurrentBuild ->
                projectModuleBuilder.buildModulesFromProject(project.project(id.projectPath))
                    .find { it.moduleIdentifier.moduleClassifier == classifier }
            id is ModuleComponentIdentifier -> {
                val metadata = getProjectStructureMetadata(
                    project,
                    component,
                    // TODO: consistent choice of configurations across multiple resolvers?
                    configurationToResolveMetadataDependencies(requestingModule.project, requestingModule),
                    moduleDependency.moduleIdentifier
                ) ?: return null
                val result = projectStructureMetadataModuleBuilder.getModule(component, metadata)
                result
            }
            else -> null
        }
    }

    companion object {
        fun getForCurrentBuild(project: Project): KpmModuleDependencyResolver {
            val extraPropertyName = "org.jetbrains.kotlin.dependencyResolution.moduleResolver.${project.getKotlinPluginVersion()}"
            return project.getOrPutRootProjectProperty(extraPropertyName) {
                val componentResultResolver = GradleComponentResultCachingResolver.getForCurrentBuild(project)
                val metadataModuleBuilder = ProjectStructureMetadataModuleBuilder()
                val projectModuleBuilder = GradleProjectModuleBuilder(true)
                KpmCachingModuleDependencyResolver(
                    KpmGradleModuleDependencyResolver(componentResultResolver, metadataModuleBuilder, projectModuleBuilder)
                )
            }
        }
    }
}

// refactor extract to a separate class/interface
// TODO think about multi-variant stub modules for non-Kotlin modules which got more than one chosen variant
internal fun buildSyntheticPlainModule(
    resolvedComponentResult: ResolvedComponentResult,
    singleVariantName: String,
): KpmExternalPlainModule {
    val moduleDependency = resolvedComponentResult.toModuleDependency()
    return KpmExternalPlainModule(KpmBasicModule(moduleDependency.moduleIdentifier).apply {
        KpmBasicVariant(this@apply, singleVariantName, DefaultLanguageSettingsBuilder()).apply {
            fragments.add(this)
            this.declaredModuleDependencies.addAll(
                resolvedComponentResult.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected.toModuleDependency() }
            )
        }
    })
}

internal class KpmExternalPlainModule(private val moduleData: KpmBasicModule) : KpmModule by moduleData {
    override fun toString(): String = "external plain $moduleData"

    val singleVariant: KpmVariant
        get() = moduleData.variants.singleOrNull()
            ?: error("synthetic $moduleData was expected to have a single variant, got: ${moduleData.variants}")
}

internal class KpmExternalImportedModule(
    private val moduleData: KpmBasicModule,
    val projectStructureMetadata: KotlinProjectStructureMetadata,
    val hostSpecificFragments: Set<KpmFragment>
) : KpmModule by moduleData {
    val hasLegacyMetadataModule = !projectStructureMetadata.isPublishedAsRoot

    override fun toString(): String = "imported $moduleData"
}

private fun ModuleComponentIdentifier.toSingleModuleIdentifier(classifier: String? = null): KpmMavenModuleIdentifier =
    KpmMavenModuleIdentifier(moduleIdentifier.group, moduleIdentifier.name, classifier)

internal fun ComponentIdentifier.matchesModule(module: KpmModule): Boolean =
    matchesModuleIdentifier(module.moduleIdentifier)

internal fun ResolvedComponentResult.toModuleIdentifiers(): List<KpmModuleIdentifier> {
    val classifiers = moduleClassifiersFromCapabilities(variants.flatMap { it.capabilities })
    return classifiers.map { moduleClassifier -> toModuleIdentifier(moduleClassifier) }
}

// FIXME this mapping doesn't have enough information to choose auxiliary modules
internal fun ResolvedComponentResult.toSingleModuleIdentifier(): KpmModuleIdentifier {
    val classifiers = moduleClassifiersFromCapabilities(variants.flatMap { it.capabilities })
    val moduleClassifier = classifiers.single() // FIXME handle multiple capabilities
    return toModuleIdentifier(moduleClassifier)
}

private fun ResolvedComponentResult.toModuleIdentifier(moduleClassifier: String?): KpmModuleIdentifier {
    return when (val id = id) {
        is ProjectComponentIdentifier -> KpmLocalModuleIdentifier(id.build.name, id.projectPath, moduleClassifier)
        is ModuleComponentIdentifier -> id.toSingleModuleIdentifier()
        else -> KpmMavenModuleIdentifier(moduleVersion?.group.orEmpty(), moduleVersion?.name.orEmpty(), moduleClassifier)
    }
}

internal fun moduleClassifiersFromCapabilities(capabilities: Iterable<Capability>): Iterable<String?> {
    val classifierCapabilities = capabilities.filter { it.name.contains("..") }
    return if (classifierCapabilities.none()) listOf(null) else classifierCapabilities.map { it.name.substringAfterLast("..") /*FIXME invent a more stable scheme*/ }
}

internal fun ComponentSelector.toModuleIdentifiers(): Iterable<KpmModuleIdentifier> {
    val moduleClassifiers = moduleClassifiersFromCapabilities(requestedCapabilities)
    return when (this) {
        is ProjectComponentSelector -> moduleClassifiers.map { KpmLocalModuleIdentifier(buildName, projectPath, it) }
        is ModuleComponentSelector -> moduleClassifiers.map { KpmMavenModuleIdentifier(moduleIdentifier.group, moduleIdentifier.name, it) }
        else -> error("unexpected component selector")
    }
}

internal fun ResolvedComponentResult.toModuleDependency(): KpmModuleDependency = KpmModuleDependency(toSingleModuleIdentifier())
internal fun ComponentSelector.toModuleDependency(): KpmModuleDependency {
    val moduleId = toModuleIdentifiers().single() // FIXME handle multiple
    return KpmModuleDependency(moduleId)
}

internal fun ComponentIdentifier.matchesModuleDependency(moduleDependency: KpmModuleDependency) =
    matchesModuleIdentifier(moduleDependency.moduleIdentifier)

internal fun ComponentIdentifier.matchesModuleIdentifier(id: KpmModuleIdentifier): Boolean =
    when (id) {
        is KpmLocalModuleIdentifier -> {
            val projectId = this as? ProjectComponentIdentifier
            projectId?.build?.name == id.buildId && projectId.projectPath == id.projectId
        }
        is KpmMavenModuleIdentifier -> {
            val componentId = this as? ModuleComponentIdentifier
            componentId?.toSingleModuleIdentifier() == id
        }
        else -> false
    }

private fun getProjectStructureMetadata(
    project: Project,
    module: ResolvedComponentResult,
    configuration: Configuration,
    moduleIdentifier: KpmModuleIdentifier? = null
): KotlinProjectStructureMetadata? {
    val extractor = if (moduleIdentifier != null)
        MppDependencyProjectStructureMetadataExtractor.create(project, module, moduleIdentifier, configuration)
    else
        MppDependencyProjectStructureMetadataExtractor.create(project, module, configuration, resolveViaAvailableAt = true)

    return extractor?.getProjectStructureMetadata()
}
