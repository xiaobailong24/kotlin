description = "kotlin-gradle-statistics"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

configureKotlinCompileTasksGradleCompatibility()

dependencies {
    api(kotlinStdlib())

    testImplementation(project(":kotlin-test:kotlin-test-junit"))
    testImplementation(commonDependency("junit:junit"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

projectTest {
    workingDir = rootDir
}
