/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.tasks

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.compilerRunner.KotlinNativeCompilerRunner
import java.net.URLClassLoader

internal abstract class NativeCompilerWorkAction : WorkAction<NativeCompilerWorkAction.NativeCompilerArguments> {
    override fun execute() {
        // We need isolated classloader here to avoid using Kotlin runtime coming via Gradle dependencies
        val classloader = URLClassLoader(parameters.compilerClasspath.files.map { it.toURI().toURL() }.toTypedArray(), null)
        val nativeMain = Class.forName("org.jetbrains.kotlin.cli.utilities.MainKt", true, classloader)
        val mainMethod = nativeMain.declaredMethods.single { it.name == "main" }
        mainMethod.invoke(nativeMain, parameters.buildArgs.get().toTypedArray())
    }

    interface NativeCompilerArguments : WorkParameters {
        val buildArgs: ListProperty<String>
        val compilerClasspath: ConfigurableFileCollection
    }

    companion object {
        fun runViaWorker(
            project: Project,
            workerExecutor: WorkerExecutor,
            buildArgs: List<String>,
        ) {
            val nativeCompilerRunner = KotlinNativeCompilerRunner(project)
            val workQueue = workerExecutor.processIsolation { spec ->
                spec.classpath.setFrom(nativeCompilerRunner.classpath)
                spec.forkOptions {
                    it.jvmArgs = nativeCompilerRunner.jvmArgs
                    it.enableAssertions = nativeCompilerRunner.enableAssertions
                    it.systemProperties = nativeCompilerRunner.cleanedSystemProperties()
                    it.environment.keys.removeAll(nativeCompilerRunner.execEnvironmentBlacklist)
                }
            }
            workQueue.submit(NativeCompilerWorkAction::class.java) { parameters ->
                parameters.buildArgs.set(nativeCompilerRunner.transformArgs(buildArgs))
                parameters.compilerClasspath.from(nativeCompilerRunner.classpath)
            }
        }
    }
}
