import org.jetbrains.kotlin.pill.PillExtension.Variant

plugins {
    id("gradle-plugin-common-configuration")
    id("jps-compatible")
}

pill {
    variant = Variant.FULL
}

dependencies {
    commonCompileOnly(project(":kotlin-gradle-plugin"))
    commonCompileOnly(project(":kotlin-gradle-plugin-api"))

    commonCompileOnly(kotlinStdlib())
    commonCompileOnly(project(":kotlin-compiler-embeddable"))

    embedded(project(":kotlinx-atomicfu-compiler-plugin"))
}

gradlePlugin {
    (plugins) {
        register("atomicfu") {
            id = "org.jetbrains.kotlin.plugin.atomicfu"
            implementationClass = "org.jetbrains.kotlinx.atomicfu.gradle.AtomicfuKotlinGradleSubplugin"
        }
    }
}

pluginBundle {
    (plugins) {
        "atomicfu" {
            id = "org.jetbrains.kotlin.plugin.atomicfu"
            description = "Kotlin compiler plugin for kotlinx.atomicfu library"
            displayName = description
            tags = tags + "atomicfu"
        }
    }
}
