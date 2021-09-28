/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.services

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.library.KotlinLibrary

class JsLibraryProvider(private val testServices: TestServices) : TestService {
    private val descriptorToDependencies = mutableMapOf<ModuleDescriptor, List<KotlinLibrary>>()
    private val descriptorToLibrary = mutableMapOf<ModuleDescriptor, KotlinLibrary>()
//    private val descriptorToLibrary = mutableMapOf<ModuleDescriptor, KotlinLibrary>()
//    private val stdlibNameToDescriptor = mutableMapOf<String, ModuleDescriptorImpl>()
//
//    fun getKotlinLibrary(moduleDescriptor: ModuleDescriptor): KotlinLibrary {
//        return descriptorToLibrary[moduleDescriptor] ?: testServices.assertions.fail {
//            "Library for module ${moduleDescriptor.name} not found"
//        }
//    }
//
//    fun getKotlinStdlibLibraryDescriptor(name: String): ModuleDescriptorImpl {
//        return stdlibNameToDescriptor[name] ?: testServices.assertions.fail {
//            "Descriptor for library ${name} not found"
//        }
//    }
//
//    fun getKotlinStdlibLibrary(name: String): KotlinLibrary {
//        return stdlibNameToDescriptor[name]?.let { descriptorToLibrary[it] } ?: testServices.assertions.fail {
//            "Library ${name} not found"
//        }
//    }
//
//    fun getOrCreateKotlinStdlibLibrary(name: String, create: (String) -> Pair<ModuleDescriptorImpl, KotlinLibrary>): ModuleDescriptorImpl {
//        return stdlibNameToDescriptor.getOrPut(name) {
//            create(name).let {
//                descriptorToLibrary += it
//                it.first
//            }
//        }
//    }
//
//    fun replaceKotlinLibraryForModule(moduleDescriptor: ModuleDescriptor, library: KotlinLibrary) {
//        require(moduleDescriptor is ModuleDescriptorImpl)
//        descriptorToLibrary[moduleDescriptor] = library
//    }
}

val TestServices.jsLibraryProvider: JsLibraryProvider by TestServices.testServiceAccessor()
