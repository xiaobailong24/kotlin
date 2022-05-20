/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bitcode

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.GenerateCompilationDatabase
import org.jetbrains.kotlin.MergeCompilationDatabases
import org.jetbrains.kotlin.ExecClang
import org.jetbrains.kotlin.cpp.CompileToExecutable
import org.jetbrains.kotlin.cpp.RunGTest
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.target.SanitizerKind
import org.jetbrains.kotlin.konan.target.supportedSanitizers
import org.jetbrains.kotlin.testing.native.GoogleTestExtension
import org.jetbrains.kotlin.utils.Maybe
import org.jetbrains.kotlin.utils.asMaybe
import java.io.File
import javax.inject.Inject

/**
 * A plugin creating extensions to compile
 */
open class CompileToBitcodePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create(EXTENSION_NAME, CompileToBitcodeExtension::class.java, target)
    }

    companion object {
        const val EXTENSION_NAME = "bitcode"
    }
}

open class CompileToBitcodeExtension @Inject constructor(val project: Project) {

    // googleTestExtension is only used if testsGroup is used.
    private val googleTestExtension by lazy { project.extensions.getByType<GoogleTestExtension>() }

    // TODO: This platformManager and execClang should acquired be from something service-ish.
    //       But for usefulness this service should be accessible from WorkAction.
    private val execClang = project.extensions.getByType<ExecClang>()
    private val platformManager = project.extensions.getByType<PlatformManager>()

    private val targetList = with(project) {
        provider { (rootProject.project(":kotlin-native").property("targetList") as? List<*>)?.filterIsInstance<String>() ?: emptyList() } // TODO: Can we make it better?
    }

    private val allMainModulesTasks by lazy {
        val name = project.name.capitalized
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            project.tasks.register("${it}$name") {
                description = "Build all main modules of $name for $it"
                group = BasePlugin.BUILD_GROUP
            }
        })
    }

    private val allTestsTasks by lazy {
        val name = project.name.capitalized
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            project.tasks.register("${it}${name}Tests") {
                description = "Runs all $name tests for $it"
                group = VERIFICATION_TASK_GROUP
            }
        })
    }

    private val compdbTasks by lazy {
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            val task = project.tasks.register("${it}${COMPILATION_DATABASE_TASK_NAME}", MergeCompilationDatabases::class.java)
            task.configure {
                outputFile = File(File(project.buildDir, it), "compile_commands.json")
            }
            task
        })
    }

    private fun addToCompdb(compileTask: CompileToBitcode) {
        val task = project.tasks.create("${compileTask.name}_CompilationDatabase", GenerateCompilationDatabase::class.java, compileTask.target, compileTask.inputFiles, compileTask.executable, compileTask.compilerFlags, compileTask.objDir)
        val compdbTask = compdbTasks[compileTask.target]!!
        compdbTask.configure {
            dependsOn(task)
            inputFiles.add(task.outputFile)
        }
    }

    fun module(name: String, srcRoot: File = project.file("src/$name"), outputGroup: String = "main", configurationBlock: CompileToBitcode.() -> Unit = {}) {
        targetList.get().forEach { targetName ->
            val target = platformManager.targetByName(targetName)
            val sanitizers: List<SanitizerKind?> = target.supportedSanitizers() + listOf(null)
            val allMainModulesTask = allMainModulesTasks[targetName]!!
            sanitizers.forEach { sanitizer ->
                val taskName = fullTaskName(name, targetName, sanitizer)
                val task = project.tasks.create(taskName, CompileToBitcode::class.java, name, targetName, outputGroup).apply {
                    srcDirs = project.files(srcRoot.resolve("cpp"))
                    headersDirs = srcDirs + project.files(srcRoot.resolve("headers"))

                    this.sanitizer = sanitizer
                    group = BasePlugin.BUILD_GROUP
                    description = "Compiles '$name' to bitcode for $targetName${sanitizer.description}"
                    dependsOn(":kotlin-native:dependencies:update")
                    configurationBlock()
                }
                addToCompdb(task)
                if (outputGroup == "main" && sanitizer == null) {
                    allMainModulesTask.configure {
                        dependsOn(taskName)
                    }
                }
            }
        }
    }

    abstract class TestsGroup @Inject constructor(
            val target: KonanTarget,
            private val _sanitizer: Maybe<SanitizerKind>,
    ) {
        val sanitizer
            get() = _sanitizer.orNull
        abstract val testedModules: ListProperty<String>
        abstract val testSupportModules: ListProperty<String>
        abstract val testLauncherModule: Property<String>
    }

    private fun createTestTask(
            testTaskName: String,
            testsGroup: TestsGroup,
    ) {
        val target = testsGroup.target
        val sanitizer = testsGroup.sanitizer
        val testName = fullTaskName(testTaskName, target.name, sanitizer)
        val testedTasks = testsGroup.testedModules.get().map {
            val name = fullTaskName(it, target.name, sanitizer)
            project.tasks.getByName(name) as CompileToBitcode
        }
        val compileToBitcodeTasks = testedTasks.mapNotNull {
            val name = "${it.name}TestBitcode"
            val task = project.tasks.findByName(name) as? CompileToBitcode
                    ?: project.tasks.create(name, CompileToBitcode::class.java, "${it.folderName}Tests", target.name, "test").apply {
                        srcDirs = it.srcDirs
                        headersDirs = it.headersDirs + googleTestExtension.headersDirs

                        this.sanitizer = sanitizer
                        excludeFiles = emptyList()
                        includeFiles = listOf("**/*Test.cpp", "**/*TestSupport.cpp", "**/*Test.mm", "**/*TestSupport.mm")
                        dependsOn(":kotlin-native:dependencies:update")
                        dependsOn("downloadGoogleTest")
                        compilerArgs.addAll(it.compilerArgs)

                        addToCompdb(this)
                    }
            if (task.inputFiles.count() == 0) null
            else task
        }
        val testFrameworkTasks = testsGroup.testSupportModules.get().map {
            val name = fullTaskName(it, target.name, sanitizer)
            project.tasks.getByName(name) as CompileToBitcode
        }

        val testSupportTask = testsGroup.testLauncherModule.get().let {
            val name = fullTaskName(it, target.name, sanitizer)
            project.tasks.getByName(name) as CompileToBitcode
        }

        val compileTask = project.tasks.register<CompileToExecutable>("${testName}Compile") {
            description = "Compile tests group '$testTaskName' for $target${sanitizer.description}"
            group = VERIFICATION_TASK_GROUP
            this.target.set(target)
            this.sanitizer.set(sanitizer)
            this.outputFile.set(project.layout.buildDirectory.file("bin/test/${target}/$testName${target.executableExtension}"))
            this.llvmLinkFirstStageOutputFile.set(project.layout.buildDirectory.file("bitcode/test/$target/$testName-firstStage.bc"))
            this.llvmLinkOutputFile.set(project.layout.buildDirectory.file("bitcode/test/$target/$testName.bc"))
            this.compilerOutputFile.set(project.layout.buildDirectory.file("obj/$target/$testName.o"))
            this.mimallocEnabled.set(testsGroup.testedModules.get().any { it.contains("mimalloc") })
            this.mainFile.set(testSupportTask.outFile)
            dependsOn(testSupportTask)
            val tasksToLink = (compileToBitcodeTasks + testedTasks + testFrameworkTasks)
            this.inputFiles.setFrom(tasksToLink.map { it.outFile })
            dependsOn(tasksToLink)
        }

        val runTask = project.tasks.register<RunGTest>(testName) {
            description = "Runs tests group '$testTaskName' for $target${sanitizer.description}"
            group = VERIFICATION_TASK_GROUP
            this.testName.set(testName)
            executable.set(compileTask.flatMap { it.outputFile })
            dependsOn(compileTask)
            reportFileUnprocessed.set(project.layout.buildDirectory.file("testReports/$testName/report.xml"))
            reportFile.set(project.layout.buildDirectory.file("testReports/$testName/report-with-prefixes.xml"))
            filter.set(project.findProperty("gtest_filter") as? String)
            tsanSuppressionsFile.set(project.layout.projectDirectory.file("tsan_suppressions.txt"))
        }

        allTestsTasks[target.name]!!.configure {
            dependsOn(runTask)
        }
    }

    fun testsGroup(
            testTaskName: String,
            action: Action<in TestsGroup>,
    ) {
        platformManager.enabled.forEach { target ->
            val sanitizers: List<SanitizerKind?> = target.supportedSanitizers() + listOf(null)
            sanitizers.forEach { sanitizer ->
                val instance = project.objects.newInstance(TestsGroup::class.java, target, sanitizer.asMaybe).apply {
                    testSupportModules.convention(listOf("googletest", "googlemock"))
                    testLauncherModule.convention("test_support")
                    action.execute(this)
                }
                createTestTask(testTaskName, instance)
            }
        }
    }

    companion object {

        private const val COMPILATION_DATABASE_TASK_NAME = "CompilationDatabase"

        const val VERIFICATION_TASK_GROUP = "verification"

        private val String.capitalized: String
            get() = replaceFirstChar { it.uppercase() }

        private fun String.snakeCaseToUpperCamelCase() = split('_').joinToString(separator = "") { it.capitalized }

        private fun fullTaskName(name: String, targetName: String, sanitizer: SanitizerKind?) = "${targetName}${name.snakeCaseToUpperCamelCase()}${sanitizer.suffix}"

        private val SanitizerKind?.suffix
            get() = when (this) {
                null -> ""
                SanitizerKind.ADDRESS -> "_ASAN"
                SanitizerKind.THREAD -> "_TSAN"
            }

        private val SanitizerKind?.description
            get() = when (this) {
                null -> ""
                SanitizerKind.ADDRESS -> " with ASAN"
                SanitizerKind.THREAD -> " with TSAN"
            }

        private val KonanTarget.executableExtension
            get() = when (this) {
                is KonanTarget.MINGW_X64 -> ".exe"
                is KonanTarget.MINGW_X86 -> ".exe"
                else -> ""
            }
    }
}
