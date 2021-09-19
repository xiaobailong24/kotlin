/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.jvm.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.diagnostics.Errors.CALLABLE_REFERENCE_TO_JAVA_SYNTHETIC_PROPERTY
import org.jetbrains.kotlin.diagnostics.Errors.UNSUPPORTED
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.psiUtil.getParentIgnoringParentheses
import org.jetbrains.kotlin.resolve.BindingContext.DELEGATE_EXPRESSION_TO_PROVIDE_DELEGATE_CALL
import org.jetbrains.kotlin.resolve.calls.callUtil.isCallableReference
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.scopes.LexicalScopeKind
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor

class UnsupportedSyntheticCallableReferenceChecker : CallChecker {
    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        // TODO: support references to synthetic Java extension properties (KT-8575)
        val ktCallableReference = reportOn.parent as? KtCallableReferenceExpression

        // We allow top-level callable reference to synthetic Java extension properties in delegate position
        if (ktCallableReference?.getParentIgnoringParentheses() is KtPropertyDelegate) return

        if (resolvedCall.call.isCallableReference() && resolvedCall.resultingDescriptor is SyntheticJavaPropertyDescriptor) {
            val diagnostic = if (
                context.languageVersionSettings.supportsFeature(LanguageFeature.NewInference) &&
                context.languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)
            ) {
                CALLABLE_REFERENCE_TO_JAVA_SYNTHETIC_PROPERTY.on(reportOn)
            } else {
                UNSUPPORTED.on(reportOn, "reference to the synthetic extension property for a Java get/set method")
            }

            context.trace.report(diagnostic)
        }
    }
}
