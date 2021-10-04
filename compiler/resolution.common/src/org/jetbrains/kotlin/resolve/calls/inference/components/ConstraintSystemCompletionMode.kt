/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

enum class ConstraintSystemCompletionMode(val isPartial: Boolean) {
    PARTIAL_WITHOUT_POSTPONED_ARGUMENTS_ANALYSIS(true),
    PARTIAL_NO_PROPER_CONSTRAINTS(true),
    PARTIAL_ILT(true),
    FULL(false),
}
