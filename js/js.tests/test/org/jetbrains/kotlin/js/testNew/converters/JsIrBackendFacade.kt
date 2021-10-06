/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testNew.converters

import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.js.config.ErrorTolerancePolicy
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.testNew.utils.extractTestPackage
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.unresolvedDependencies
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.test.backend.ir.IrBackendFacade
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.frontend.classic.moduleDescriptorProvider
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.*
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator
import java.io.File

class JsIrBackendFacade(
    testServices: TestServices
) : IrBackendFacade<BinaryArtifacts.Js>(testServices, ArtifactKinds.Js) {
    override fun transform(module: TestModule, inputArtifact: IrBackendInput): BinaryArtifacts.Js? {
        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)
        val isMainModule = JsEnvironmentConfigurator.isMainModule(module, testServices)
        val project = testServices.compilerConfigurationProvider.getProject(module)

        val input = inputArtifact.jsBackendInput!!
        if (isMainModule) {
            val testPackage = extractTestPackage(testServices)
            val mainCallParameters = JsEnvironmentConfigurator.getMainCallParametersForModule(module)
            val lowerPerModule = JsEnvironmentConfigurationDirectives.LOWER_PER_MODULE in module.directives
            val compiledModule = compileIr(
                input.moduleFragment,
                MainModule.SourceFiles(input.sourceFiles), // TODO MainModule.Klib if needed
                configuration,
                input.dependencyModules,
                input.moduleFragment.irBuiltins,
                input.symbolTable!!,
                input.deserializer!!,
                input.moduleFragmentToUniqueName,
                PhaseConfig(jsPhases), // TODO debug mode
                if (lowerPerModule) PersistentIrFactory() else IrFactoryImpl,
                mainArguments = mainCallParameters.run { if (shouldBeGenerated()) arguments() else null },
                exportedDeclarations = setOf(FqName.fromSegments(listOfNotNull(testPackage, JsEnvironmentConfigurator.TEST_FUNCTION))),
                generateFullJs = true,
                generateDceJs = JsEnvironmentConfigurationDirectives.RUN_IR_DCE in module.directives,
                dceDriven = false,
                dceRuntimeDiagnostic = null,
                es6mode = JsEnvironmentConfigurationDirectives.RUN_ES6_MODE in module.directives,
                multiModule = JsEnvironmentConfigurationDirectives.SPLIT_PER_MODULE in module.directives ||
                        JsEnvironmentConfigurationDirectives.PER_MODULE in module.directives,
                relativeRequirePath = false,
                propertyLazyInitialization = JsEnvironmentConfigurationDirectives.PROPERTY_LAZY_INITIALIZATION in module.directives,
                legacyPropertyAccess = false,
                baseClassIntoMetadata = false,
                lowerPerModule = lowerPerModule,
                safeExternalBoolean = JsEnvironmentConfigurationDirectives.SAFE_EXTERNAL_BOOLEAN in module.directives,
                safeExternalBooleanDiagnostic = module.directives[JsEnvironmentConfigurationDirectives.SAFE_EXTERNAL_BOOLEAN_DIAGNOSTIC].singleOrNull()
            )

            val runIrPir = JsEnvironmentConfigurationDirectives.RUN_IR_PIR in module.directives
            val dontSkipDceDriven = JsEnvironmentConfigurationDirectives.SKIP_DCE_DRIVEN !in module.directives
            val pirCompiledModule = if (runIrPir && !dontSkipDceDriven) {
                compileIr(
                    input.moduleFragment,
                    MainModule.SourceFiles(input.sourceFiles), // TODO MainModule.Klib if needed
                    configuration,
                    input.dependencyModules,
                    input.moduleFragment.irBuiltins,
                    input.symbolTable!!,
                    input.deserializer!!,
                    input.moduleFragmentToUniqueName,
                    PhaseConfig(jsPhases), // TODO debug mode
                    PersistentIrFactory(),
                    mainArguments = mainCallParameters.run { if (shouldBeGenerated()) arguments() else null },
                    exportedDeclarations = setOf(FqName.fromSegments(listOfNotNull(testPackage, JsEnvironmentConfigurator.TEST_FUNCTION))),
                    generateFullJs = true,
                    generateDceJs = false,
                    dceDriven = true,
                    dceRuntimeDiagnostic = null,
                    es6mode = JsEnvironmentConfigurationDirectives.RUN_ES6_MODE in module.directives,
                    multiModule = JsEnvironmentConfigurationDirectives.SPLIT_PER_MODULE in module.directives ||
                            JsEnvironmentConfigurationDirectives.PER_MODULE in module.directives,
                    relativeRequirePath = false,
                    propertyLazyInitialization = JsEnvironmentConfigurationDirectives.PROPERTY_LAZY_INITIALIZATION in module.directives,
                    legacyPropertyAccess = false,
                    baseClassIntoMetadata = false,
                    lowerPerModule = false,
                    safeExternalBoolean = JsEnvironmentConfigurationDirectives.SAFE_EXTERNAL_BOOLEAN in module.directives,
                    safeExternalBooleanDiagnostic = module.directives[JsEnvironmentConfigurationDirectives.SAFE_EXTERNAL_BOOLEAN_DIAGNOSTIC].singleOrNull()

                )
            } else null

            val outputFile = File(JsEnvironmentConfigurator.getJsModuleArtifactPath(testServices, module.name) + ".js")

            // TODO("Write to handler")
            return BinaryArtifacts.JsIrArtifact(outputFile, compiledModule, pirCompiledModule)
        } else {
            val outputFile = JsEnvironmentConfigurator.getJsKlibArtifactPath(testServices, module.name)
            val errorPolicy = configuration.get(JSConfigurationKeys.ERROR_TOLERANCE_POLICY) ?: ErrorTolerancePolicy.DEFAULT
            val hasErrors = TopDownAnalyzerFacadeForJSIR.checkForErrors(input.sourceFiles, input.bindingContext, errorPolicy)

//            val stdlib = JsEnvironmentConfigurator.getStdlibPathsForModule(module).map {
//                testServices.jsLibraryProvider.getOrCreateStdlibByPath(it) {
//                    testServices.assertions.fail { "Library with path $it wasn't found" }
//                }
//            }

            val dependencies = testServices.moduleDescriptorProvider.getModuleDescriptor(module).allDependencyModules.map { it as ModuleDescriptorImpl }
            val allDependencies = dependencies.map { testServices.jsLibraryProvider.getCompiledLibraryByDescriptor(it) }

            serializeModuleIntoKlib(
                configuration[CommonConfigurationKeys.MODULE_NAME]!!,
                project,
                configuration,
                configuration.get(IrMessageLogger.IR_MESSAGE_LOGGER) ?: IrMessageLogger.None,
                input.bindingContext,
                input.sourceFiles,
                klibPath = outputFile,
                allDependencies,
                input.moduleFragment,
                input.expectDescriptorToSymbol,
                cleanFiles = emptyList(),
                nopack = true,
                perFile = false,
                containsErrorCode = hasErrors,
                abiVersion = KotlinAbiVersion.CURRENT, // TODO get from test file data
                jsOutputName = null
            )

            val lib = jsResolveLibraries(
                dependencies.map { testServices.jsLibraryProvider.getPathByDescriptor(it) } + listOf(outputFile),
                configuration[JSConfigurationKeys.REPOSITORIES] ?: emptyList(),
                configuration[IrMessageLogger.IR_MESSAGE_LOGGER].toResolverLogger()
            ).getFullResolvedList().last().library

            val moduleDescriptor = JsFactories.DefaultDeserializedDescriptorFactory.createDescriptorOptionalBuiltIns(
                lib,
                configuration.languageVersionSettings,
                LockBasedStorageManager("ModulesStructure"),
                testServices.moduleDescriptorProvider.getModuleDescriptor(module).builtIns,
                packageAccessHandler = null,
                lookupTracker = LookupTracker.DO_NOTHING
            )
            moduleDescriptor.setDependencies(dependencies + moduleDescriptor)
            testServices.moduleDescriptorProvider.replaceModuleDescriptorForModule(module, moduleDescriptor)

            return BinaryArtifacts.JsKlibArtifact(File(outputFile), moduleDescriptor, lib)
        }
    }
}