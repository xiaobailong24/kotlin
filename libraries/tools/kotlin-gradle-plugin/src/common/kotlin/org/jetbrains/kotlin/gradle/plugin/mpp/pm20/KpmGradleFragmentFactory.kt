/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KpmGradleFragmentFactory.FragmentConfigurator
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KpmGradleFragmentFactory.FragmentInstantiator

/**
 * Factory used by [KpmGradleModule] to polymorphic-ally create fragments/variants.
 * Fragments are created in two stages with this factory:
 *
 * [FragmentInstantiator]:
 * An Instantiator (unlike something called a 'Factory') will just provide a new instance.
 * All objects referenced from the Fragment shall be already created and accessing them should be safe,
 * whilst some additional configuration still hase to be done in the configuration step.
 *
 * [FragmentConfigurator]:
 *  After the fragment is instantiated, additional configuration (depending on the fragment instance) can be done.
 *  Typical configurations are:
 *   - Further setting up attributes of configurations (based upon the fragment itself)
 *   - Setting up additional Gradle tasks
 *   - Setting up publication
 */
class KpmGradleFragmentFactory<T : GradleKpmFragment>(
    private val fragmentInstantiator: FragmentInstantiator<T>,
    private val fragmentConfigurator: FragmentConfigurator<T>
) : NamedDomainObjectFactory<T> {

    /**
     * @see KpmGradleFragmentFactory
     */
    interface FragmentInstantiator<out T : GradleKpmFragment> {
        fun create(name: String): T
    }

    /**
     * @see KpmGradleFragmentFactory
     */
    interface FragmentConfigurator<in T : GradleKpmFragment> {
        fun configure(fragment: T)
    }

    override fun create(name: String): T {
        return fragmentInstantiator.create(name).apply(fragmentConfigurator::configure)
    }
}

internal fun <T : GradleKpmFragment> Configuration.configure(
    definition: KotlinGradleFragmentConfigurationDefinition<T>, fragment: T
) {
    definition.attributes.setAttributes(attributes, fragment)
    definition.artifacts.addArtifacts(outgoing, fragment)
    definition.capabilities.setCapabilities(outgoing, fragment)
}
