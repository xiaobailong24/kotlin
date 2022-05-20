/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util

import org.gradle.api.Project
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.internal.build.BuildState
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast
import org.jetbrains.kotlin.project.model.KpmModule
import org.jetbrains.kotlin.project.model.KpmLocalModuleIdentifier

fun KpmModule.representsProject(project: Project): Boolean =
    moduleIdentifier.let { it is KpmLocalModuleIdentifier && it.buildId == project.currentBuildId().name && it.projectId == project.path }

// FIXME internal API?
fun Project.currentBuildId(): BuildIdentifier =
    (project as ProjectInternal).services.get(BuildState::class.java).buildIdentifier


/**
 * The base name to use for archive files.
 */
val Project.archivesName get() = if (isGradleVersionAtLeast(7, 1)) {
        extensions.getByType(BasePluginExtension::class.java).archivesName.orNull
    } else {
        convention.findPlugin(BasePluginConvention::class.java)?.archivesBaseName
    }

/**
 * Returns the directory to generate TAR and ZIP archives into.
 *
 * @return The directory. Never returns null.
 */
val Project.distsDirectory get() = if (isGradleVersionAtLeast(7, 1)) {
        extensions.getByType(BasePluginExtension::class.java).distsDirectory
    } else {
        convention.getPlugin(BasePluginConvention::class.java).distsDirectory
    }
