/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import org.jetbrains.kotlin.project.model.*
import org.jetbrains.kotlin.project.model.utils.variantsContainingFragment
import javax.inject.Inject

open class KpmGradleModuleInternal(
    final override val project: Project,
    final override val moduleClassifier: String?
) : KpmGradleModule {

    @Inject
    constructor(project: Project, moduleName: CharSequence) : this(
        project,
        moduleName.takeIf { it != KpmGradleModule.MAIN_MODULE_NAME }?.toString()
    )

    override val moduleIdentifier: KpmModuleIdentifier =
        KpmLocalModuleIdentifier(project.currentBuildId().name, project.path, moduleClassifier)

    override val fragments: ExtensiblePolymorphicDomainObjectContainer<GradleKpmFragment> =
        project.objects.polymorphicDomainObjectContainer(GradleKpmFragment::class.java)

    // TODO DSL & build script model: find a way to create a flexible typed view on fragments?
    override val variants: NamedDomainObjectSet<GradleKpmVariant> by lazy {
        fragments.withType(GradleKpmVariant::class.java)
    }

    override val plugins: Set<KpmCompilerPlugin> by lazy {
        mutableSetOf<KpmCompilerPlugin>().also { set ->
            project
                .plugins
                .withType(KpmGradleCompilerPlugin::class.java)
                .mapTo(set, KpmGradleCompilerPlugin::kpmCompilerPlugin)
        }
    }

    override var isPublic: Boolean = false
        protected set

    private var setPublicHandlers: MutableList<() -> Unit> = mutableListOf()

    override fun ifMadePublic(action: () -> Unit) {
        // FIXME reentrancy?
        if (isPublic) action() else setPublicHandlers.add(action)
    }

    override fun makePublic() {
        if (isPublic) return
        setPublicHandlers.forEach { it() }
        isPublic = true
    }

    companion object {
        const val MAIN_MODULE_NAME = "main"
        const val TEST_MODULE_NAME = "test"
    }

    override fun toString(): String = "$moduleIdentifier (Gradle)"
}

internal val KpmGradleModule.resolvableMetadataConfigurationName: String
    get() = lowerCamelCaseName(name, "DependenciesMetadata")

internal val KpmGradleModule.isMain
    get() = moduleIdentifier.moduleClassifier == null

internal fun KpmGradleModule.disambiguateName(simpleName: String) =
    lowerCamelCaseName(moduleClassifier, simpleName)

internal fun KpmGradleModule.variantsContainingFragment(fragment: KpmFragment): Iterable<GradleKpmVariant> =
    (this as KpmModule).variantsContainingFragment(fragment).onEach { it as GradleKpmVariant } as Iterable<GradleKpmVariant>
