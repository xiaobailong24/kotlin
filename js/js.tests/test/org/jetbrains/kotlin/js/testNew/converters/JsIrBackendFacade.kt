/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testNew.converters

import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.js.testNew.utils.extractTestPackage
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.test.backend.ir.IrBackendFacade
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.compilerConfigurationProvider
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.moduleStructure
import java.io.File

class JsIrBackendFacade(
    testServices: TestServices
) : IrBackendFacade<BinaryArtifacts.Js>(testServices, ArtifactKinds.Js) {
    override fun transform(module: TestModule, inputArtifact: IrBackendInput): BinaryArtifacts.Js? {
        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)
        val isMainModule = JsEnvironmentConfigurator.isMainModule(module, testServices)

        if (isMainModule) {
            val testPackage = extractTestPackage(testServices)
            val mainCallParameters = JsEnvironmentConfigurator.getMainCallParametersForModule(module)
            val compiledModule = compileIr(
                moduleFragment,
                mainModule,
                configuration,
                dependencyModules,
                irBuiltIns,
                symbolTable,
                deserializer,
                moduleToName,
                PhaseConfig(jsPhases), // TODO debug mode
                irFactory,
                mainArguments = mainCallParameters.run { if (shouldBeGenerated()) arguments() else null },
                exportedDeclarations = setOf(FqName.fromSegments(listOfNotNull(testPackage, JsEnvironmentConfigurator.TEST_FUNCTION))),
                generateFullJs = true,
                generateDceJs = JsEnvironmentConfigurationDirectives.RUN_IR_DCE in module.directives,
                dceDriven = false,
                dceRuntimeDiagnostic = null,
                es6mode = JsEnvironmentConfigurationDirectives.RUN_ES6_MODE in module.directives,
                multiModule = JsEnvironmentConfigurationDirectives.RUN_ES6_MODE in module.directives ||
                        JsEnvironmentConfigurationDirectives.PER_MODULE in module.directives,
                relativeRequirePath = false,
                propertyLazyInitialization = JsEnvironmentConfigurationDirectives.PROPERTY_LAZY_INITIALIZATION in module.directives,
                legacyPropertyAccess = false,
                baseClassIntoMetadata = false,
                lowerPerModule = JsEnvironmentConfigurationDirectives.LOWER_PER_MODULE in module.directives,
                safeExternalBoolean = JsEnvironmentConfigurationDirectives.SAFE_EXTERNAL_BOOLEAN in module.directives,
                safeExternalBooleanDiagnostic = module.directives[JsEnvironmentConfigurationDirectives.SAFE_EXTERNAL_BOOLEAN_DIAGNOSTIC].singleOrNull()
            )

            // TODO("Write to")
            return BinaryArtifacts.JsIrArtifact(File(""), compiledModule, null)
        } else {
            val outputFile = JsEnvironmentConfigurator.getJsKlibArtifactPath(testServices, module.name)

            serializeModuleIntoKlib(
                configuration[CommonConfigurationKeys.MODULE_NAME]!!,
                testServices.compilerConfigurationProvider.getProject(module),
                configuration,
                configuration.get(IrMessageLogger.IR_MESSAGE_LOGGER) ?: IrMessageLogger.None,
                depsDescriptors.jsFrontEndResult.bindingContext,
                files,
                klibPath = outputFile,
                allDependencies,
                moduleFragment,
                expectDescriptorToSymbol,
                cleanFiles = emptyList(),
                nopack = true,
                perFile = false,
                depsDescriptors.jsFrontEndResult.hasErrors,
                abiVersion = KotlinAbiVersion.CURRENT, // TODO get from test file data
                jsOutputName = null
            )

            return BinaryArtifacts.JsKlibArtifact(File(outputFile))
        }
    }
}