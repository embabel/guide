// TODO: TEMPORARY GRADLE BUILD FOR DRIVINE KSP CODE GENERATION
//
// Why: Maven's third-party KSP plugin (kotlin-maven-symbol-processing) hasn't been
// updated for Kotlin 2.2.0 yet. It has a binary incompatibility with the new compiler
// plugin API. This Gradle build is a temporary workaround to generate Drivine DSL code.
//
// When to remove: Once kotlin-maven-symbol-processing v1.7+ is released with Kotlin 2.2.0
// support, we can move the KSP configuration back to pom.xml and delete this directory.
//
// How it works:
// 1. This Gradle build runs KSP on the domain classes
// 2. Generated code goes to build/generated/ksp/main/kotlin
// 3. Maven build includes those generated sources via build-helper-maven-plugin
//
// To run code generation: ./gradlew :codegen-gradle:kspKotlin

plugins {
    kotlin("jvm") version "2.2.0"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    java  // Enable Java plugin for Java source processing
}

group = "com.embabel.guide"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Drivine core library
    implementation("org.drivine:drivine4j:0.0.3-SNAPSHOT")

    // KSP processor for code generation
    ksp("org.drivine:drivine4j-codegen:0.0.3-SNAPSHOT")

    // JetBrains annotations (needed for @NotNull, @Nullable in Java sources)
    implementation("org.jetbrains:annotations:26.0.1")

    // Domain classes that need DSL generation
    // TODO: Add your domain model classes here
    // implementation(project(":")) // Reference parent project if needed
}

// Configure Java source sets to include parent project
java {
    sourceSets {
        main {
            java.srcDirs("../src/main/java")
        }
    }
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
                "../src/main/kotlin",  // Parent project Kotlin sources
                "build/generated/ksp/main/kotlin"  // Generated sources
            )
        }
    }
}

// Configure KSP to generate code in a specific location
ksp {
    arg("option1", "value1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("kspKotlin")
}
