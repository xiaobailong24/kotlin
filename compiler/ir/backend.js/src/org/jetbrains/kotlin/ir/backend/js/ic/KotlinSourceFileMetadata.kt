/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.protobuf.CodedInputStream
import org.jetbrains.kotlin.protobuf.CodedOutputStream

@JvmInline
value class KotlinLibraryFile(val path: String) {
    constructor(lib: KotlinLibrary) : this(lib.libraryFile.canonicalPath)

    fun toProtoStream(out: CodedOutputStream) = out.writeStringNoTag(path)

    companion object {
        fun fromProtoStream(input: CodedInputStream) = KotlinLibraryFile(input.readString())
    }
}

@JvmInline
value class KotlinSourceFile(val path: String) {
    constructor(irFile: IrFile) : this(irFile.fileEntry.name)

    fun toProtoStream(out: CodedOutputStream) = out.writeStringNoTag(path)

    companion object {
        fun fromProtoStream(input: CodedInputStream) = KotlinSourceFile(input.readString())
    }
}

open class KotlinSourceFileMap<out T>(files: Map<KotlinLibraryFile, Map<KotlinSourceFile, T>>) :
    Map<KotlinLibraryFile, Map<KotlinSourceFile, T>> by files {

    inline fun forEachFile(f: (KotlinLibraryFile, KotlinSourceFile, T) -> Unit) =
        forEach { (lib, files) -> files.forEach { (file, data) -> f(lib, file, data) } }

    operator fun get(libFile: KotlinLibraryFile, sourceFile: KotlinSourceFile): T? = get(libFile)?.get(sourceFile)
}

class KotlinSourceFileMutableMap<T>(
    private val files: MutableMap<KotlinLibraryFile, MutableMap<KotlinSourceFile, T>> = mutableMapOf()
) : KotlinSourceFileMap<T>(files) {

    operator fun set(libFile: KotlinLibraryFile, sourceFile: KotlinSourceFile, data: T) = getOrPutFiles(libFile).put(sourceFile, data)
    operator fun set(libFile: KotlinLibraryFile, sourceFiles: MutableMap<KotlinSourceFile, T>) = files.put(libFile, sourceFiles)

    fun getOrPutFiles(libFile: KotlinLibraryFile) = files.getOrPut(libFile) { mutableMapOf() }

    fun copyFilesFrom(other: KotlinSourceFileMap<T>) {
        for ((libFile, srcFiles) in other) {
            files.getOrPut(libFile) { mutableMapOf() } += srcFiles
        }
    }

    fun removeFile(libFile: KotlinLibraryFile, sourceFile: KotlinSourceFile) {
        val libFiles = files[libFile]
        if (libFiles != null) {
            libFiles.remove(sourceFile)
            if (libFiles.isEmpty()) {
                files.remove(libFile)
            }
        }
    }

    fun clear() = files.clear()
}

fun <T> KotlinSourceFileMap<T>.toMutable(): KotlinSourceFileMutableMap<T> {
    return KotlinSourceFileMutableMap(entries.associateTo(mutableMapOf()) { it.key to it.value.toMutableMap() })
}

fun KotlinSourceFileMap<Set<IdSignature>>.flatSignatures(): Set<IdSignature> {
    val allSignatures = mutableSetOf<IdSignature>()
    forEachFile { _, _, signatures -> allSignatures += signatures }
    return allSignatures
}

fun KotlinSourceFileMutableMap<MutableSet<IdSignature>>.addSignature(
    lib: KotlinLibraryFile, src: KotlinSourceFile, signature: IdSignature
) = when (val signatures = this[lib, src]) {
    null -> this[lib, src] = mutableSetOf(signature)
    else -> signatures += signature
}

abstract class KotlinSourceFileExports {
    abstract val inverseDependencies: KotlinSourceFileMap<Set<IdSignature>>

    open fun getExportedSignatures(): Set<IdSignature> = inverseDependencies.flatSignatures()
}

abstract class KotlinSourceFileMetadata : KotlinSourceFileExports() {
    abstract val directDependencies: KotlinSourceFileMap<Set<IdSignature>>

    abstract val importedInlineFunctions: Map<IdSignature, ICHash>
}

fun KotlinSourceFileMetadata.isEmpty() = inverseDependencies.isEmpty() && directDependencies.isEmpty() && importedInlineFunctions.isEmpty()



