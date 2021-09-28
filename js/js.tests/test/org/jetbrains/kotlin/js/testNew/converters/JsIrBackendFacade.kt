/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testNew.converters

import org.jetbrains.kotlin.test.backend.ir.IrBackendFacade
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

class JsIrBackendFacade(
    testServices: TestServices
) : IrBackendFacade<BinaryArtifacts.Js>(testServices, ArtifactKinds.Js) {
    override fun transform(module: TestModule, inputArtifact: IrBackendInput): BinaryArtifacts.Js? {

        TODO("Not yet implemented")
    }
}