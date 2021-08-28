/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testNew.handlers

import org.jetbrains.kotlin.js.backend.ast.JsExpressionStatement
import org.jetbrains.kotlin.js.backend.ast.JsNullLiteral
import org.jetbrains.kotlin.js.backend.ast.JsProgram
import org.jetbrains.kotlin.js.backend.ast.RecursiveJsVisitor
import org.jetbrains.kotlin.js.test.utils.DirectiveTestUtils
import org.jetbrains.kotlin.test.backend.handlers.JsBinaryArtifactHandler
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.isKtFile
import org.junit.Assert

class JsAstHandler(testServices: TestServices) : JsBinaryArtifactHandler(testServices) {
    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {}

    override fun processModule(module: TestModule, info: BinaryArtifacts.Js) {
        val ktFiles = module.files.filter { it.isKtFile }.map { it.originalContent }
        val program = (info as? BinaryArtifacts.OldJsArtifact)?.jsProgram
            ?: throw AssertionError("JsBoxRunner suppose to work only with old js backend")
        processJsProgram(program, ktFiles)
    }

    companion object {
        fun processJsProgram(program: JsProgram, psiFiles: List<String>) {
            psiFiles.forEach { DirectiveTestUtils.processDirectives(program, it) }
            program.verifyAst()
        }

        private fun JsProgram.verifyAst() {
            accept(object : RecursiveJsVisitor() {
                override fun visitExpressionStatement(x: JsExpressionStatement) {
                    when (x.expression) {
                        is JsNullLiteral -> Assert.fail("Expression statement contains `null` literal")
                        else -> super.visitExpressionStatement(x)
                    }
                }
            })
        }
    }
}