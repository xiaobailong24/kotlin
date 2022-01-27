/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.optimizations

import kotlinx.cinterop.toCValues
import llvm.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.llvm.*
import org.jetbrains.kotlin.backend.konan.llvm.getBasicBlocks
import org.jetbrains.kotlin.backend.konan.llvm.getFunctions
import org.jetbrains.kotlin.backend.konan.llvm.getInstructions
import org.jetbrains.kotlin.backend.konan.llvm.llvmContext

var removed = 0
var total = 0

private fun filterLoads(block: LLVMBasicBlockRef, variable: LLVMValueRef) = getInstructions(block)
        .mapNotNull { LLVMIsALoadInst(it) }
        .filter { inst ->
            LLVMGetOperand(inst, 0)?.let { LLVMIsAGlobalVariable(it) } == variable
        }

private fun process(function: LLVMValueRef, currentThreadTLV: LLVMValueRef) {
    val entry = LLVMGetEntryBasicBlock(function) ?: return
    val load = filterLoads(entry, currentThreadTLV).firstOrNull() ?: return
    getBasicBlocks(function)
            .flatMap { filterLoads(it, currentThreadTLV) }
            .filter { it != load }
            .toList() // to force evaluating of all sequences above, because removing something during iteration is bad idea
            .forEach {
                LLVMReplaceAllUsesWith(it, load)
                LLVMInstructionEraseFromParent(it)
            }
}

internal fun process2(function: LLVMValueRef, module: LLVMModuleRef) {
    val builder = LLVMCreateBuilderInContext(llvmContext)

    val global = LLVMGetNamedGlobal(module, "_ZN6kotlin2mm8internal20gSuspensionRequestedE")!!
    val slowPath = LLVMGetNamedFunction(module, "_ZN6kotlin2mm26SuspendIfRequestedSlowPathEv")!!
    getBasicBlocks(function)
            .flatMap { getInstructions(it) }
            .mapNotNull { LLVMIsACallInst(it) }
            .filter { LLVMGetCalledValue(it)?.name == "Kotlin_mm_safePointFunctionPrologue" }
            .drop(1)
            .toList() // to force evaluating of all sequences above, because removing something during iteration is bad idea
            .forEach {
                total += 1
                removed += 1
                require(LLVMGetFirstUse(it) == null)
                LLVMInstructionEraseFromParent(it)
            }

    for (block in getBasicBlocks(function).toList()) {
        val calls = getInstructions(block)
                .mapNotNull { LLVMIsACallInst(it) }
                .filter {
                    LLVMGetCalledValue(it)?.name == "_ZN6kotlin2mm18SuspendIfRequestedEv" ||
                            LLVMGetCalledValue(it)?.name == "Kotlin_mm_safePointFunctionPrologue"
                }
                .toList()
        total += calls.size
        for (call in calls.drop(1)) {
            removed += 1
            require(LLVMGetFirstUse(call) == null)
            LLVMInstructionEraseFromParent(call)
        }
        val call = calls.firstOrNull() ?: continue
        val rem = LLVMSplitBasicBlock(block, call)
        LLVMInstructionEraseFromParent(LLVMGetLastInstruction(block))
        LLVMInstructionEraseFromParent(call)
        LLVMPositionBuilderAtEnd(builder, block)
        val load = LLVMBuildLoad(builder,
                LLVMConstGEP(global, listOf(Int64(0).llvm, Int32(0).llvm, Int32(0).llvm).toCValues(), 3),
                ""
        )
        LLVMSetOrdering(load, LLVMAtomicOrdering.LLVMAtomicOrderingSequentiallyConsistent)
        val eq = LLVMBuildICmp(builder, LLVMIntPredicate.LLVMIntEQ, load, Int32(0).llvm, "")
        val slowBlock = LLVMAppendBasicBlockInContext(llvmContext, function, "")
        LLVMBuildCondBr(builder, eq, rem, slowBlock)
        LLVMPositionBuilderAtEnd(builder, slowBlock)
        LLVMBuildCall(builder, slowPath, emptyList<LLVMValueRef>().toCValues(), 0, "")
        LLVMBuildBr(builder, rem)
        LLVMMoveBasicBlockAfter(rem, block)
        LLVMMoveBasicBlockAfter(slowBlock, block)
    }


    LLVMDisposeBuilder(builder)
}

internal fun removeMultipleThreadDataLoads(context: Context) {
    val currentThreadTLV = context.llvm.runtimeAnnotationMap["current_thread_tlv"]?.singleOrNull() ?: return

    getFunctions(context.llvmModule!!)
            .filter { it.name?.startsWith("kfun:") == true }
            .filterNot { LLVMIsDeclaration(it) == 1 }
            .forEach {
                process(it, currentThreadTLV)
                process2(it, context.llvmModule!!)
            }

    println("total _ZN6kotlin2mm18SuspendIfRequestedEv calls: $total")
    println("removed _ZN6kotlin2mm18SuspendIfRequestedEv calls: $removed")
    context.verifyBitCode()
}