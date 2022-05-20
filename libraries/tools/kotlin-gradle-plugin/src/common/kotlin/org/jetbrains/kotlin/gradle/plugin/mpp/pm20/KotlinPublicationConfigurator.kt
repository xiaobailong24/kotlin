/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

interface KotlinPublicationConfigurator<in T : GradleKpmVariant> : KpmGradleFragmentFactory.FragmentConfigurator<T> {

    object NoPublication : KotlinPublicationConfigurator<GradleKpmVariant> {
        override fun configure(fragment: GradleKpmVariant) = Unit
    }

    object SingleVariantPublication : KotlinPublicationConfigurator<GradleKpmPublishedVariantWithRuntime> {
        override fun configure(fragment: GradleKpmPublishedVariantWithRuntime) {
            VariantPublishingConfigurator.get(fragment.project).configureSingleVariantPublication(fragment)
        }
    }

    object NativeVariantPublication : KotlinPublicationConfigurator<GradleKpmNativeVariantInternal> {
        override fun configure(fragment: GradleKpmNativeVariantInternal) {
            VariantPublishingConfigurator.get(fragment.project).configureNativeVariantPublication(fragment)
        }
    }
}
