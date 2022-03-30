/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types;

/**
 * We convert Java types differently, depending on where they occur in the Java code
 * This enum encodes the kinds of occurrences
 */
public enum TypeUsage {
    SUPERTYPE,
    COMMON
}
