// Gradle build for Drivine KSP code generation
//
// Why: Maven's third-party KSP plugin (kotlin-maven-symbol-processing) hasn't been
// updated for Kotlin 2.2.0 yet. This Gradle build is a workaround to generate Drivine DSL code.
//
// How it works:
// 1. This Gradle build runs KSP on the domain classes
// 2. Generated code goes to build/generated/ksp/main/kotlin
// 3. Maven build includes those generated sources via build-helper-maven-plugin
//
// To run code generation: ./gradlew kspKotlin

plugins {
    kotlin("jvm") version "2.2.0"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

group = "com.embabel.guide"
version = "0.1.0-SNAPSHOT"

val drivineVersion = "0.0.28"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://repo.embabel.com/artifactory/libs-snapshot")
    }
    maven {
        url = uri("https://repo.embabel.com/artifactory/libs-release")
    }
}

dependencies {
    // Drivine core library
    implementation("org.drivine:drivine4j:$drivineVersion")

    // KSP processor for code generation
    ksp("org.drivine:drivine4j-codegen:$drivineVersion")

    // Dependencies needed for domain classes to compile
    implementation("com.embabel.agent:embabel-agent-api:0.3.2-SNAPSHOT")
    implementation("com.embabel.agent:embabel-agent-discord:0.3.2-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
}

kotlin {
    compilerOptions {
        // Required for Drivine DSL with context parameters
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }

    // Configure source sets to read from parent project
    sourceSets {
        main {
            kotlin.srcDirs(
                "../src/main/kotlin",
                "build/generated/ksp/main/kotlin"
            )
        }
    }
}