/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.protobuf.CodedInputStream
import org.jetbrains.kotlin.protobuf.CodedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal inline fun <T> File.ifExists(f: File.() -> T): T? = if (exists()) f() else null

internal fun File.recreate() {
    if (exists()) {
        delete()
    } else {
        parentFile?.mkdirs()
    }
    createNewFile()
}

internal inline fun <T> File.useCodedInputIfExists(f: CodedInputStream.() -> T) = ifExists {
    FileInputStream(this).use {
        CodedInputStream.newInstance(it).f()
    }
}

internal inline fun File.useCodedOutput(f: CodedOutputStream.() -> Unit) {
    parentFile?.mkdirs()
    recreate()
    FileOutputStream(this).use {
        val out = CodedOutputStream.newInstance(it)
        out.f()
        out.flush()
    }
}

internal fun icError(msg: String): Nothing = error("IC internal error: $msg")


internal fun notFoundIcError(what: String, libFile: KotlinLibraryFile? = null, srcFile: KotlinSourceFile? = null): Nothing {
    val filePath = listOfNotNull(libFile?.path, srcFile?.path).joinToString(":") { File(it).name }
    val msg = if (filePath.isEmpty()) what else "$what for $filePath"
    icError("can not find $msg")
}

internal inline fun <E> buildListUntil(to: Int, builderAction: MutableList<E>.(Int) -> Unit): List<E> {
    return buildList(to) { repeat(to) { builderAction(it) } }
}

internal inline fun <E> buildSetUntil(to: Int, builderAction: MutableSet<E>.(Int) -> Unit): Set<E> {
    return buildSet(to) { repeat(to) { builderAction(it) } }
}

internal inline fun <K, V> buildMapUntil(to: Int, builderAction: MutableMap<K, V>.(Int) -> Unit): Map<K, V> {
    return buildMap(to) { repeat(to) { builderAction(it) } }
}
