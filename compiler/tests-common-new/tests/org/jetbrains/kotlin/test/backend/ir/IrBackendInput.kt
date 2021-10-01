/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.backend.js.KotlinFileSerializedData
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.linkage.IrDeserializer
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.library.resolver.KotlinResolvedLibrary
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.test.model.BackendKinds
import org.jetbrains.kotlin.test.model.ResultingArtifact

// IR backend (JVM, JS, Native)
data class IrBackendInput(
    val backendInput: JvmIrCodegenFactory.JvmIrBackendInput?,
    val jsBackendInput: JsIrBackendInput? = null
) : ResultingArtifact.BackendInput<IrBackendInput>() {
    override val kind: BackendKinds.IrBackend
        get() = BackendKinds.IrBackend
}

data class JsIrBackendInput(
    val moduleFragment: IrModuleFragment,
    val sourceFiles: List<KtFile>,
    val dependencyModules: List<IrModuleFragment>,
    val symbolTable: SymbolTable?,
    val bindingContext: BindingContext,
    val expectDescriptorToSymbol: MutableMap<DeclarationDescriptor, IrSymbol>,
    val deserializer: JsIrLinker? = null,
    val moduleFragmentToUniqueName: Map<IrModuleFragment, String>
)