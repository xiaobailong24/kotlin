plugins {
    kotlin("multiplatform").version("<pluginMarkerVersion>")
}

repositories {
    mavenLocal()
    maven("../repo")
    mavenCentral()
}

kotlin {
    iosX64 {
        binaries.framework {
            export("com.example:lib:1.0")
        }
    }
    iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("com.example:lib:1.0")
            }
        }
    }
}
