/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testNew.handlers

import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.CompilationOutputs
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.testNew.converters.ClassicJsBackendFacade.Companion.wrapWithModuleEmulationMarkers
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.test.backend.handlers.JsBinaryArtifactHandler
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.compilerConfigurationProvider
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator
import java.io.File

class JsIrArtifactsDumpHandler(testServices: TestServices) : JsBinaryArtifactHandler(testServices) {
    override fun processModule(module: TestModule, info: BinaryArtifacts.Js) {
        info as BinaryArtifacts.JsIrArtifact
        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)
        val moduleId = configuration.getNotNull(CommonConfigurationKeys.MODULE_NAME)
        val moduleKind = configuration.get(JSConfigurationKeys.MODULE_KIND, ModuleKind.PLAIN)
        val outputFile = File(JsEnvironmentConfigurator.getJsModuleArtifactPath(testServices, module.name) + ".js")
        val outputDceFile = File(JsEnvironmentConfigurator.getDceJsArtifactPath(testServices, module.name) + ".js")
        val outputPirFile = File(JsEnvironmentConfigurator.getPirJsArtifactPath(testServices, module.name) + ".js")

        info.compilerResult.outputs!!.writeTo(outputFile, moduleId, moduleKind)
        info.compilerResult.outputsAfterDce?.writeTo(outputDceFile, moduleId, moduleKind)
        info.pirCompilerResult?.outputs?.writeTo(outputPirFile, moduleId, moduleKind)
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {}

    private fun CompilationOutputs.writeTo(outputFile: File, moduleId: String, moduleKind: ModuleKind) {
        val wrappedCode = wrapWithModuleEmulationMarkers(jsCode, moduleId = moduleId, moduleKind = moduleKind)
        outputFile.write(wrappedCode)

        val dependencyPaths = mutableListOf<String>()

        dependencies.forEach { (moduleId, outputs) ->
            val moduleWrappedCode = wrapWithModuleEmulationMarkers(outputs.jsCode, moduleKind, moduleId)
            val dependencyPath = outputFile.absolutePath.replace("_v5.js", "-${moduleId}_v5.js")
            dependencyPaths += dependencyPath
            File(dependencyPath).write(moduleWrappedCode)
        }
    }

    private fun File.write(text: String) {
        parentFile.mkdirs()
        writeText(text)
    }
}