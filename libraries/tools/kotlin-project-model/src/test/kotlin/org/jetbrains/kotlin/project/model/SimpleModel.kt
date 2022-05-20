/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.project.model

fun module(name: String, classifier: String? = null) = BasicKotlinModule(LocalModuleIdentifier("current", name, classifier))

fun BasicKotlinModule.fragment(vararg nameParts: String): KpmBasicFragment =
    fragment(nameParts.drop(1).joinToString("", nameParts.first()) { it.capitalize() })

fun BasicKotlinModule.fragment(name: String): KpmBasicFragment =
    fragments.firstOrNull { it.fragmentName == name } ?: KpmBasicFragment(this, name).also { fragments.add(it) }

fun BasicKotlinModule.variant(vararg nameParts: String): KpmBasicVariant =
    variant(nameParts.drop(1).joinToString("", nameParts.first()) { it.capitalize() })

fun BasicKotlinModule.variant(name: String): KpmBasicVariant =
    fragments.firstOrNull { it.fragmentName == name }
        ?.let { it as? KpmBasicVariant ?: error("$name is not a variant") }
        ?: KpmBasicVariant(this, name).also { fragments.add(it) }

fun KotlinModuleIdentifier.equalsWithoutClassifier(other: KotlinModuleIdentifier) = when (this) {
    is LocalModuleIdentifier -> other is LocalModuleIdentifier &&
            LocalModuleIdentifier(buildId, projectId, null) == LocalModuleIdentifier(other.buildId, other.projectId, null)
    is MavenModuleIdentifier -> other is MavenModuleIdentifier &&
            MavenModuleIdentifier(group, name, null) == MavenModuleIdentifier(other.group, other.name, null)
    else -> error("can't check equality yet")
}

fun KpmBasicFragment.depends(module: BasicKotlinModule) {
    this.declaredModuleDependencies += KotlinModuleDependency(module.moduleIdentifier)
}

fun KpmBasicFragment.refinedBy(fragment: KpmBasicFragment) {
    fragment.refines(this)
}

fun KpmBasicFragment.refines(fragment: KpmBasicFragment) {
    require(fragment.containingModule == containingModule)
    declaredRefinesDependencies.add(fragment)
}

// ---

internal data class ModuleBundle(val modules: List<BasicKotlinModule>) {
    val main: BasicKotlinModule
        get() = modules.single { it.moduleIdentifier.moduleClassifier == null }

    operator fun get(modulePurpose: String): BasicKotlinModule = when (modulePurpose) {
        "main" -> main
        else -> modules.single { it.moduleIdentifier.moduleClassifier == modulePurpose }
    }
}

internal fun simpleModuleBundle(name: String): ModuleBundle {
    fun createModule(purpose: String): BasicKotlinModule =
        module(name, purpose.takeIf { it != "main" }).apply {
            val common = fragment("common")

            val (jvm, js, linux) = listOf("jvm", "js", "linux").map { platform ->
                variant(platform).apply {
                    variantAttributes[KotlinPlatformTypeAttribute] = when (platform) {
                        "jvm" -> KotlinPlatformTypeAttribute.JVM
                        "js" -> KotlinPlatformTypeAttribute.JS
                        else -> {
                            variantAttributes[KotlinNativeTargetAttribute] = platform
                            KotlinPlatformTypeAttribute.NATIVE
                        }
                    }
                }
            }

            fragment("jvmAndJs").apply {
                refines(common)
                refinedBy(jvm)
                refinedBy(js)
            }
            fragment("jsAndLinux").apply {
                refines(common)
                refinedBy(js)
                refinedBy(linux)
            }
        }

    val main = createModule("main")
    val test = createModule("test")

    test.fragment("common").depends(main)

    return ModuleBundle(listOf(main, test))
}
