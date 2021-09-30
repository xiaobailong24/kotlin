/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testNew.converters

import org.jetbrains.kotlin.backend.common.phaser.invokeToplevel
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.lower.generateTests
import org.jetbrains.kotlin.ir.backend.js.lower.moveBodilessDeclarationsToSeparatePlace
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.StageController
import org.jetbrains.kotlin.ir.declarations.persistent.PersistentIrFactory
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.noUnboundLeft
import org.jetbrains.kotlin.test.backend.ir.IrBackendFacade
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.compilerConfigurationProvider
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator

class JsIrBackendFacade(
    testServices: TestServices
) : IrBackendFacade<BinaryArtifacts.Js>(testServices, ArtifactKinds.Js) {
    override fun transform(module: TestModule, inputArtifact: IrBackendInput): BinaryArtifacts.Js? {
        val configuration = testServices.compilerConfigurationProvider.getCompilerConfiguration(module)
        val isMainModule = JsEnvironmentConfigurator.isMainModule(module, testServices)
        val klibMainModule = JsEnvironmentConfigurationDirectives.KLIB_MAIN_MODULE in module.directives

        if (!isMainModule) {
            TODO("reuse from compile")
        } else {
            if (klibMainModule) {
                TODO("must support several compilation of single module")
            } else {
//                serializeModuleIntoKlib(
//                    configuration[CommonConfigurationKeys.MODULE_NAME]!!,
//                    project,
//                    configuration,
//                    messageLogger,
//                    depsDescriptors.jsFrontEndResult.bindingContext,
//                    files,
//                    outputKlibPath,
//                    allDependencies,
//                    moduleFragment,
//                    expectDescriptorToSymbol,
//                    icData,
//                    nopack,
//                    perFile = false,
//                    depsDescriptors.jsFrontEndResult.hasErrors,
//                    abiVersion,
//                    jsOutputName
//                )
            }
        }
        TODO("Not yet implemented")
    }
}