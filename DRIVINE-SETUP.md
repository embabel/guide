# Drivine 0.0.3-SNAPSHOT Setup

This project has been upgraded to **Drivine 0.0.3-SNAPSHOT** with annotation-based configuration and Kotlin 2.2.0.

## ✅ What's Working

### Annotation-Based Configuration
- **`@EnableDrivine`** - Enables core Drivine infrastructure beans
- **`@EnableDrivinePropertiesConfig`** - Auto-configures DataSourceMap from `application.yml`
- **`@EnableDrivineTestConfig`** - Smart test configuration with automatic Testcontainers support

### Kotlin 2.2.0 with Context Parameters
- Enabled via `-Xcontext-parameters` compiler flag
- Required for Drivine's type-safe DSL features
- Fully compatible with all Drivine features

### Automatic KSP Code Generation
- **Status**: ✅ Working (via temporary Gradle integration)
- **Integration**: Seamlessly runs during Maven build
- **No manual steps required** - just run `mvn compile` as usual

## ⚠️ Temporary Gradle Integration

### Why?
Maven's third-party KSP plugin (`kotlin-maven-symbol-processing`) hasn't been updated for Kotlin 2.2.0 yet. It has a binary incompatibility with the new compiler plugin API.

### How It Works
1. Maven automatically runs `gradlew kspKotlin` during `generate-sources` phase
2. Gradle processes `@GraphView` annotated classes with KSP
3. Generated DSL code is placed in `codegen-gradle/build/generated/ksp/main/kotlin`
4. Maven includes the generated code in compilation

### Completely Seamless
```bash
# Just run Maven as normal!
mvn clean install

# KSP code generation happens automatically
# No manual Gradle commands needed
```

### Files Involved
- **`codegen-gradle/`** - Minimal Gradle build for KSP only
- **`pom.xml`** - `exec-maven-plugin` runs Gradle, `build-helper-maven-plugin` includes generated sources
- **See**: [codegen-gradle/README.md](codegen-gradle/README.md) for technical details

## Configuration Examples

### Main Application (`DrivineConfig.java`)
```java
@Configuration
@EnableDrivine
@EnableDrivinePropertiesConfig
public class DrivineConfig {

    @Bean("neo")
    public PersistenceManager neoManager(PersistenceManagerFactory factory) {
        return factory.get("neo");
    }
}
```

**Before** (manual configuration):
- Manual `dataSourceMap()` bean creation
- Manual URI parsing and `ConnectionProperties` construction
- 60+ lines of boilerplate code

**After** (annotation-based):
- 3 lines of configuration
- Properties read from `application.yml`
- Clean and maintainable

### Test Configuration (`TestApplicationContext.kt`)
```kotlin
@Configuration
@EnableDrivine
@EnableDrivineTestConfig
@ComponentScan(basePackages = ["org.drivine", "com.embabel"])
@EnableAspectJAutoProxy(proxyTargetClass = true)
class TestAppContext
```

**Before**:
- Manual `Neo4jTestContainer` singleton
- Custom `Neo4jPropertiesInitializer`
- Manual `dataSourceMap()` bean for tests
- Complex property initialization logic

**After**:
- Simple annotation configuration
- Automatic Testcontainers management
- Built-in local/CI mode switching

### Datasource Properties (`application-test.yml`)
```yaml
database:
  datasources:
    neo:
      host: localhost
      port: 7687
      username: neo4j
      password: brahmsian
      type: NEO4J
      database-name: neo4j
```

**Toggle between local Neo4j and Testcontainers:**
- **Local dev**: Set environment variable `USE_LOCAL_NEO4J=true`
- **CI/Testcontainers**: Default (no environment variable needed)

## Using the Type-Safe DSL

When you annotate a class with `@GraphView`, KSP automatically generates a type-safe DSL:

```kotlin
@GraphView
data class GuideUser(
    val id: String,
    val displayName: String,
    val persona: String?
)
```

Generated DSL (automatic):
```kotlin
// Extension functions for type-safe queries
fun PersistenceManager.findGuideUsers(
    context(FilterContext) block: GuideUserFilter.() -> Unit
): List<GuideUser>

// Usage in your code
val users = manager.findGuideUsers {
    displayName eq "John"
    persona.isNotNull()
}
```

## Building the Project

### Regular Development
```bash
# KSP runs automatically
mvn clean install

# Or just compile
mvn compile
```

### First Time Setup
```bash
# Ensure Gradle wrapper is executable
chmod +x codegen-gradle/gradlew

# Build normally
mvn clean install
```

### IDE Integration
IntelliJ IDEA will automatically pick up:
- Generated DSL code (after first Maven build)
- Context parameters syntax
- Type-safe query builders

**Note**: Run Maven build once after adding new `@GraphView` classes to generate their DSLs.

## Removed Files (Migration Complete)

The following files were replaced by annotation-based configuration:

- ❌ `Neo4jTestContainer.kt` → Replaced by `@EnableDrivineTestConfig`
- ❌ `Neo4jPropertiesInitializer.kt` → Replaced by `@EnableDrivineTestConfig`
- ❌ Manual `dataSourceMap()` bean in `DrivineConfig` → Replaced by `@EnableDrivinePropertiesConfig`
- ❌ Profile-specific bean creation logic → Replaced by properties

## Future Cleanup

**When `kotlin-maven-symbol-processing` v1.7+ supports Kotlin 2.2.0:**

1. Delete `codegen-gradle/` directory
2. Remove `exec-maven-plugin` and `build-helper-maven-plugin` from `pom.xml`
3. Re-enable native Maven KSP (commented sections in POM have the config)
4. Delete this "Temporary Gradle Integration" section

**Expected timeframe**: A few months (Maven plugin maintainers typically update within 1-2 releases)

## Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| Drivine4j | 0.0.3-SNAPSHOT | Core library + Spring Boot starter |
| Drivine4j-codegen | 0.0.3-SNAPSHOT | KSP processor (Gradle only) |
| Kotlin | 2.2.0 | With context parameters |
| KSP | 2.2.20-2.0.4 | Via Gradle (temporary) |
| Spring Boot | 3.5.7 | Parent POM |
| Neo4j Driver | 5.x | Managed by Drivine |

## Troubleshooting

### "Source root doesn't exist" Warning
```
[WARNING] Source root doesn't exist: .../codegen-gradle/build/generated/ksp/main/kotlin
```
**Normal**: Appears when no `@GraphView` classes exist yet. Warning disappears once you add annotated classes.

### Gradle Build Fails
```bash
# Clean Gradle cache
cd codegen-gradle
./gradlew clean

# Rebuild
cd ..
mvn clean compile
```

### Generated Code Not Visible in IDE
1. Run `mvn clean compile` once
2. Refresh Maven project in IntelliJ (Ctrl+Shift+O / Cmd+Shift+I)
3. Generated sources should appear under `target/generated-sources/`

## Resources

- **Drivine README**: https://github.com/liberation-data/drivine4j/blob/main/README.md
- **KSP Documentation**: https://kotlinlang.org/docs/ksp-overview.html
- **Context Parameters**: https://kotlinlang.org/docs/whatsnew-eap.html#context-parameters
- **Drivine Source**: `/Users/jblues/embabel/drivine4j`

## Summary

This setup gives you:
- ✅ Clean annotation-based configuration
- ✅ Automatic test infrastructure (Testcontainers)
- ✅ Type-safe DSL code generation
- ✅ Kotlin 2.2.0 with latest features
- ✅ Seamless Maven build (Gradle runs automatically)
- ✅ No manual code generation steps

The Gradle integration is temporary but completely transparent. Your Maven workflow remains unchanged!
