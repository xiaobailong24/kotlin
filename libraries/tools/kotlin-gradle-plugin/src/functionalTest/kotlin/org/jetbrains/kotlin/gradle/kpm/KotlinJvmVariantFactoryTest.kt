/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.kpm

import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KotlinJvmVariantFactoryTest : AbstractKpmExtensionTest() {
    private val testAttribute = Attribute.of("this.test", String::class.java)

    @Test
    fun `test platform type`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        assertEquals(KotlinPlatformType.jvm, variant.platformType)
        assertEquals("jvm", variant.fragmentName)
    }

    @Test
    fun `test accessing configurations`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")

        variant.compileDependenciesConfiguration
        variant.runtimeDependenciesConfiguration
        variant.apiElementsConfiguration
        variant.runtimeElementsConfiguration
        variant.implementationConfiguration
        variant.apiConfiguration
        variant.runtimeOnlyConfiguration
        variant.compileOnlyConfiguration
    }

    @Test
    fun `test compilationData`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        assertSame(variant, variant.compilationData.owner)
    }

    @Test
    fun `test accessing compileDependencyFiles`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        variant.compileDependencyFiles.files
    }

    @Test
    fun `test accessing runtimeDependencyFiles`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        variant.runtimeDependencyFiles.files
    }

    @Test
    fun `test accessing runtimeFiles`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        variant.runtimeFiles.files
    }

    @Test
    fun `test has source roots`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        assertTrue(variant.kotlinSourceRoots.srcDirs.isNotEmpty())
    }

    @Test
    fun `test sourceArchiveTaskName registered`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        assertTrue(project.tasks.getByName(variant.sourceArchiveTaskName) is Zip)
    }

    @Test
    fun `test compileTask registered`() {
        val variant = KpmJvmVariantFactory(kotlin.main).create("jvm")
        assertTrue(project.tasks.getByName(variant.compilationData.compileKotlinTaskName) is KotlinCompile)
    }

    @Test
    fun `test custom configure compileDependenciesConfiguration`() {
        val variant = KpmJvmVariantFactory(
            kotlin.main, KpmJvmVariantConfig(
                compileDependencies = DefaultKotlinCompileDependenciesDefinition + FragmentAttributes {
                    assertSame(fragment.compileDependenciesConfiguration.attributes, attributes)
                    attribute(testAttribute, "compileDependencies")
                }
            )
        ).create("jvm")

        assertEquals(
            "compileDependencies", variant.compileDependenciesConfiguration.attributes.getAttribute(testAttribute)
        )
    }

    @Test
    fun `test custom configure runtimeDependenciesConfiguration`() {
        val variant = KpmJvmVariantFactory(
            kotlin.main, KpmJvmVariantConfig(
                runtimeDependencies = DefaultKotlinRuntimeDependenciesDefinition + FragmentAttributes {
                    assertSame(fragment.runtimeDependenciesConfiguration.attributes, attributes)
                    attribute(testAttribute, "runtimeDependencies")
                }
            )
        ).create("jvm")

        assertEquals(
            "runtimeDependencies", variant.runtimeDependenciesConfiguration.attributes.getAttribute(testAttribute)
        )
    }

    @Test
    fun `test custom configure apiElementsConfiguration`() {
        val variant = KpmJvmVariantFactory(
            kotlin.main, KpmJvmVariantConfig(
                apiElements = DefaultKotlinApiElementsDefinition + FragmentAttributes {
                    assertSame(fragment.apiElementsConfiguration.attributes, attributes)
                    attribute(testAttribute, "apiElements")
                }
            )
        ).create("jvm")

        assertEquals(
            "apiElements", variant.apiElementsConfiguration.attributes.getAttribute(testAttribute)
        )
    }

    @Test
    fun `test custom configure runtimeElementsConfiguration`() {
        val variant = KpmJvmVariantFactory(
            kotlin.main, KpmJvmVariantConfig(
                runtimeElements = DefaultKotlinRuntimeElementsDefinition + FragmentAttributes {
                    assertSame(fragment.runtimeElementsConfiguration.attributes, attributes)
                    attribute(testAttribute, "runtimeElements")
                }
            )
        ).create("jvm")

        assertEquals(
            "runtimeElements", variant.runtimeElementsConfiguration.attributes.getAttribute(testAttribute)
        )
    }

    @Test
    fun `test custom sourceDirectories configuration`() {
        val variant = KpmJvmVariantFactory(
            kotlin.main, KpmJvmVariantConfig(
                sourceDirectoriesConfigurator = object : KotlinSourceDirectoriesConfigurator<KpmJvmVariant> {
                    override fun configure(fragment: KpmJvmVariant) {
                        fragment.kotlinSourceRoots.setSrcDirs(listOf(project.file("src/abc/kotlin")))
                    }
                }
            )
        ).create("jvm")

        assertEquals(listOf(project.file("src/abc/kotlin")), variant.kotlinSourceRoots.srcDirs.toList())
    }
}
