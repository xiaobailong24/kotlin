/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.project.model

import org.jetbrains.kotlin.project.model.utils.variantsContainingFragment
import org.jetbrains.kotlin.tooling.core.closure
import org.jetbrains.kotlin.tooling.core.withClosure
import java.io.File

interface KpmFragment {
    val containingModule: KotlinModule

    val fragmentName: String

    val languageSettings: LanguageSettings?

    // TODO: should this be source roots or source files?
    val kotlinSourceRoots: Iterable<File>

    // TODO: scopes
    val declaredModuleDependencies: Iterable<KotlinModuleDependency>

    val declaredRefinesDependencies: Iterable<KpmFragment>

    val refinesClosure: Set<KpmFragment>
        get() = this.closure { it.declaredRefinesDependencies }

    val withRefinesClosure: Set<KpmFragment>
        get() = this.withClosure { it.declaredRefinesDependencies }

    companion object
}

val KpmFragment.fragmentAttributeSets: Map<KotlinAttributeKey, Set<String>>
    get() = mutableMapOf<KotlinAttributeKey, MutableSet<String>>().apply {
        containingModule.variantsContainingFragment(this@fragmentAttributeSets).forEach { variant ->
            variant.variantAttributes.forEach { (attribute, value) ->
                getOrPut(attribute) { mutableSetOf() }.add(value)
            }
        }
    }

val KpmVariant.platform get() = variantAttributes[KotlinPlatformTypeAttribute]

open class KpmBasicFragment(
    override val containingModule: KotlinModule,
    override val fragmentName: String,
    override val languageSettings: LanguageSettings? = null
) : KpmFragment {

    override val declaredRefinesDependencies: MutableSet<KpmBasicFragment> = mutableSetOf()

    override val declaredModuleDependencies: MutableSet<KotlinModuleDependency> = mutableSetOf()

    override var kotlinSourceRoots: Iterable<File> = emptyList()

    override fun toString(): String = "fragment $fragmentName"
}

