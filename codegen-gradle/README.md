# Drivine KSP Code Generation (Temporary Gradle Build)

## Why This Exists

This is a **temporary workaround** for generating Drivine DSL code with KSP (Kotlin Symbol Processing).

### The Problem
- Maven's third-party KSP plugin (`kotlin-maven-symbol-processing`) hasn't been updated for Kotlin 2.2.0
- It has a binary incompatibility with Kotlin 2.2.0's compiler plugin API
- KSP works perfectly with Gradle + Kotlin 2.2.0

### The Solution
- Use this minimal Gradle build ONLY for code generation
- Generated code is placed in `build/generated/ksp/main/kotlin`
- Maven build includes the generated sources via `build-helper-maven-plugin`

## How to Use

### Generate Code
```bash
cd codegen-gradle
./gradlew kspKotlin
```

### Clean Generated Code
```bash
cd codegen-gradle
./gradlew clean
```

## When to Remove This

Once `kotlin-maven-symbol-processing` v1.7+ (or later) is released with Kotlin 2.2.0 support:

1. Remove this `codegen-gradle` directory
2. Re-enable KSP configuration in `pom.xml` (see commented-out section)
3. Update the Maven build to use native KSP support

## Files Generated

KSP generates DSL extension functions for classes annotated with `@GraphView`:
- `{ClassName}Dsl.kt` - Type-safe query builders
- Located in same package as source class
- Automatically registered with IntelliJ for code completion

## Project Structure

```
codegen-gradle/
├── build.gradle.kts          # Gradle build for KSP
├── settings.gradle.kts        # Gradle settings
├── src/main/kotlin/          # Symlink to ../src/main/kotlin (optional)
└── build/generated/          # Generated code output
    └── ksp/main/kotlin/
```
