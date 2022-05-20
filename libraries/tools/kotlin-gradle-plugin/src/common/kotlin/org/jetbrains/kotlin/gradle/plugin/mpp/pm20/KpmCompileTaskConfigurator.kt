/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.gradle.api.tasks.TaskProvider

interface KpmCompileTaskConfigurator<in T : KpmGradleVariant> {
    fun registerCompileTasks(variant: T): TaskProvider<*>
}

object KpmJvmCompileTaskConfigurator : KpmCompileTaskConfigurator<KpmJvmVariant> {
    override fun registerCompileTasks(variant: KpmJvmVariant): TaskProvider<*> {
        val compilationData = variant.compilationData
        LifecycleTasksManager(variant.project).registerClassesTask(compilationData)
        return KpmCompilationTaskConfigurator(variant.project).createKotlinJvmCompilationTask(variant, compilationData)
    }
}

object KpmNativeCompileTaskConfigurator : KpmCompileTaskConfigurator<KpmNativeVariantInternal> {
    override fun registerCompileTasks(variant: KpmNativeVariantInternal): TaskProvider<*> {
        val compilationData = variant.compilationData
        LifecycleTasksManager(variant.project).registerClassesTask(compilationData)
        return KpmCompilationTaskConfigurator(variant.project).createKotlinNativeCompilationTask(variant, compilationData)
    }
}


