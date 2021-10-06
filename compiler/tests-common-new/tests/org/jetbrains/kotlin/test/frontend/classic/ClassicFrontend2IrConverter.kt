/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.classic

import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureDescriptor
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.backend.js.generateIrForKlibSerialization
import org.jetbrains.kotlin.ir.backend.js.getIrModuleInfoForSourceFiles
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsManglerDesc
import org.jetbrains.kotlin.ir.backend.js.sortDependencies
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.js.config.ErrorTolerancePolicy
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.psi2ir.Psi2IrConfiguration
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.backend.ir.JsIrBackendInput
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.BackendKinds
import org.jetbrains.kotlin.test.model.Frontend2BackendConverter
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.*
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator

class ClassicFrontend2IrConverter(
    testServices: TestServices
) : Frontend2BackendConverter<ClassicFrontendOutputArtifact, IrBackendInput>(
    testServices,
    FrontendKinds.ClassicFrontend,
    BackendKinds.IrBackend
) {
    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::JsLibraryProvider))

    override fun transform(module: TestModule, inputArtifact: ClassicFrontendOutputArtifact): IrBackendInput {
        return when (module.targetBackend) {
            TargetBackend.JVM_IR -> transformToJvmIr(module, inputArtifact)
            TargetBackend.JS_IR -> transformToJsIr(module, inputArtifact)
            else -> testServices.assertions.fail { "Target backend ${module.targetBackend} not supported for transformation into IR" }
        }
    }

    private fun transformToJvmIr(module: TestModule, inputArtifact: ClassicFrontendOutputArtifact): IrBackendInput {
        val (psiFiles, analysisResult, project, _) = inputArtifact

        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)

        val files = psiFiles.values.toList()
        val phaseConfig = configuration.get(CLIConfigurationKeys.PHASE_CONFIG) ?: PhaseConfig(jvmPhases)
        val codegenFactory = JvmIrCodegenFactory(configuration, phaseConfig)
        val state = GenerationState.Builder(
            project, ClassBuilderFactories.TEST, analysisResult.moduleDescriptor, analysisResult.bindingContext,
            files, configuration
        ).codegenFactory(codegenFactory)
            .isIrBackend(true)
            .build()

        val ignoreErrors = CodegenTestDirectives.IGNORE_ERRORS in module.directives
        return IrBackendInput(codegenFactory.convertToIr(state, files, ignoreErrors))
    }

    private fun transformToJsIr(module: TestModule, inputArtifact: ClassicFrontendOutputArtifact): IrBackendInput {
        val (psiFiles, analysisResult, project, _) = inputArtifact

        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)
        val isMainModule = JsEnvironmentConfigurator.isMainModule(module, testServices)
        val klibMainModule = JsEnvironmentConfigurationDirectives.KLIB_MAIN_MODULE in module.directives
        val verifySignatures = JsEnvironmentConfigurationDirectives.SKIP_MANGLE_VERIFICATION !in module.directives

//        val stdlib = JsEnvironmentConfigurator.getStdlibPathsForModule(module).map {
//            testServices.jsLibraryProvider.getOrCreateStdlibByPath(it) {
//                testServices.assertions.fail { "Library with path $it wasn't found" }
//            }
//        }

        val dependencies = testServices.moduleDescriptorProvider.getModuleDescriptor(module).allDependencyModules
        val allDependencies = dependencies.associateBy { testServices.jsLibraryProvider.getCompiledLibraryByDescriptor(it) }

        val expectDescriptorToSymbol = mutableMapOf<DeclarationDescriptor, IrSymbol>()
        if (!isMainModule) {
            // 1. generate Klib
            val moduleFragment = generateIrForKlibSerialization(
                project,
                psiFiles.values.toList(),
                configuration,
                analysisResult,
                sortDependencies(allDependencies),
                mutableListOf(),
                expectDescriptorToSymbol,
                IrFactoryImpl,
                verifySignatures
            ) {
                testServices.jsLibraryProvider.getDescriptorByCompiledLibrary(it)
            }

            return IrBackendInput(
                null,
                JsIrBackendInput(
                    moduleFragment,
                    psiFiles.values.toList(),
                    emptyList(),
                    symbolTable = null,
                    bindingContext = analysisResult.bindingContext,
                    expectDescriptorToSymbol = expectDescriptorToSymbol,
                    deserializer = null,
                    moduleFragmentToUniqueName = emptyMap()
                )
            )
        } else {
            val messageLogger = configuration.get(IrMessageLogger.IR_MESSAGE_LOGGER) ?: IrMessageLogger.None
            val signaturer = IdSignatureDescriptor(JsManglerDesc)
            val runIrPir = JsEnvironmentConfigurationDirectives.RUN_IR_PIR in module.directives
            val lowerPerModule = JsEnvironmentConfigurationDirectives.LOWER_PER_MODULE in module.directives
            val dontSkipDceDriven = JsEnvironmentConfigurationDirectives.SKIP_DCE_DRIVEN !in module.directives

            val irFactory = if (lowerPerModule || (runIrPir && dontSkipDceDriven)) {
                PersistentIrFactory()
            } else {
                IrFactoryImpl
            }
            val symbolTable = SymbolTable(signaturer, irFactory)

            // 2. compile
            if (klibMainModule) {
//                val mainPath = JsEnvironmentConfigurator.getJsKlibArtifactPath(testServices, module.name)
//                val mainResolvedLibrary = allDependencies.find { it.library.libraryFile.canonicalPath == mainPath }
//                    ?: error("No module with ${mainPath} found")
//                val mainModuleLib = mainResolvedLibrary.library
//                val sortedDependencies = sortDependencies(listOf(mainResolvedLibrary), depsDescriptors.descriptors)
//                val moduleInfo = getIrModuleInfoForKlib(
//                    analysisResult.moduleDescriptor,
//                    sortedDependencies,
//                    configuration,
//                    symbolTable,
//                    messageLogger,
//                    emptyMap(),
//                    { depsDescriptors.modulesWithCaches(it) },
//                    { depsDescriptors.getModuleDescriptor(it) },
//                )
                TODO("must support several compilation of single module")
            } else {
                val errorPolicy = configuration.get(JSConfigurationKeys.ERROR_TOLERANCE_POLICY) ?: ErrorTolerancePolicy.DEFAULT

                val psi2Ir = Psi2IrTranslator(
                    configuration.languageVersionSettings,
                    Psi2IrConfiguration(errorPolicy.allowErrors)
                )
                val psi2IrContext = psi2Ir.createGeneratorContext(
                    analysisResult.moduleDescriptor,
                    analysisResult.bindingContext,
                    symbolTable
                )

                val moduleInfo = getIrModuleInfoForSourceFiles(
                    psi2IrContext,
                    project,
                    configuration,
                    psiFiles.values.toList(),
                    sortDependencies(allDependencies),
                    emptyMap(),
                    symbolTable,
                    messageLogger,
                    verifySignatures,
                    { emptySet() },
                    { testServices.jsLibraryProvider.getDescriptorByCompiledLibrary(it) },
                )

                return IrBackendInput(
                    null,
                    JsIrBackendInput(
                        moduleInfo.module,
                        psiFiles.values.toList(),
                        dependencyModules = moduleInfo.allDependencies,
                        symbolTable = moduleInfo.symbolTable,
                        bindingContext = analysisResult.bindingContext,
                        expectDescriptorToSymbol = expectDescriptorToSymbol,
                        deserializer = moduleInfo.deserializer,
                        moduleFragmentToUniqueName = moduleInfo.moduleFragmentToUniqueName
                    )
                )
            }
        }
    }
}
