/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.capabilities.Capability
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinGradleFragmentConfigurationCapabilities.CapabilitiesContainer

/* Internal abbreviation */
internal typealias FragmentCapabilities<T> = KotlinGradleFragmentConfigurationCapabilities<T>

interface KotlinGradleFragmentConfigurationCapabilities<in T : GradleKpmFragment> {
    interface CapabilitiesContainer {
        fun capability(notation: Any)
    }

    fun setCapabilities(container: CapabilitiesContainer, fragment: T)

    object None : KotlinGradleFragmentConfigurationCapabilities<GradleKpmFragment> {
        override fun setCapabilities(container: CapabilitiesContainer, fragment: GradleKpmFragment) = Unit
    }
}

fun <T : GradleKpmFragment> KotlinGradleFragmentConfigurationCapabilities<T>.setCapabilities(
    publications: ConfigurationPublications, fragment: T
) = setCapabilities(CapabilitiesContainer(publications), fragment)

fun CapabilitiesContainer(configuration: ConfigurationPublications): CapabilitiesContainer =
    CapabilitiesContainerImpl(configuration)

fun CapabilitiesContainer(configuration: Configuration): CapabilitiesContainer =
    CapabilitiesContainerImpl(configuration.outgoing)

private class CapabilitiesContainerImpl(
    private val publications: ConfigurationPublications
) : CapabilitiesContainer {
    override fun capability(notation: Any) = publications.capability(notation)
}

class KotlinGradleFragmentConfigurationCapabilitiesContext<T : GradleKpmFragment> internal constructor(
    internal val container: CapabilitiesContainer, val fragment: T
) : CapabilitiesContainer by container {
    val project: Project get() = fragment.project
}

fun <T : GradleKpmFragment> FragmentCapabilities(
    setCapabilities: KotlinGradleFragmentConfigurationCapabilitiesContext<T>.() -> Unit
): KotlinGradleFragmentConfigurationCapabilities<T> = object : KotlinGradleFragmentConfigurationCapabilities<T> {
    override fun setCapabilities(container: CapabilitiesContainer, fragment: T) {
        val context = KotlinGradleFragmentConfigurationCapabilitiesContext(container, fragment)
        context.setCapabilities()
    }
}

operator fun <T : GradleKpmFragment> FragmentCapabilities<T>.plus(other: FragmentCapabilities<T>): FragmentCapabilities<T> {
    if (this === KotlinGradleFragmentConfigurationCapabilities.None) return other
    if (other === KotlinGradleFragmentConfigurationCapabilities.None) return this

    if (this is CompositeFragmentCapabilities && other is CompositeFragmentCapabilities) {
        return CompositeFragmentCapabilities(this.children + other.children)
    }

    if (this is CompositeFragmentCapabilities) {
        return CompositeFragmentCapabilities(this.children + other)
    }

    if (other is CompositeFragmentCapabilities) {
        return CompositeFragmentCapabilities(listOf(this) + other.children)
    }

    return CompositeFragmentCapabilities(listOf(this, other))
}

internal class CompositeFragmentCapabilities<in T : GradleKpmFragment>(val children: List<FragmentCapabilities<T>>) :
    FragmentCapabilities<T> {
    override fun setCapabilities(container: CapabilitiesContainer, fragment: T) {
        children.forEach { child -> child.setCapabilities(container, fragment) }
    }
}
