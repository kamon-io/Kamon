---
name: sbt-to-mill-migration
description: Migrate Kamon modules from SBT to Mill build system following project conventions for naming, dependencies, shading, and testing
---

# SBT to Mill Migration Guide for Kamon

This skill documents the conventions and procedures for migrating Kamon modules from SBT to Mill build system.

## Project Structure

### Directory Layout

- Mill build files use `package.mill` in subdirectories (e.g., `core/package.mill`, `reporters/package.mill`)
- Main build configuration lives in `build.mill` at the project root
- Source code follows SBT layout: `src/main/scala`, `src/test/scala`, `src/main/resources`, etc.
- **When migrating**: Rename the module directory from `reporters/kamon-{name}/` to `reporters/{name}/` (drop the `kamon-` prefix). Do NOT copy individual files - rename the entire directory to preserve git history and avoid leaving old directories behind.

### Module Naming Conventions

- **Mill module names**: Use simple names WITHOUT the `kamon-` prefix (e.g., `prometheus`, `datadog`, `opentelemetry`). Use camelCase if there are several words in the module name.
- **Artifact names**: Preserve the original `kamon-*` naming via `artifactName` override
- **Cross-compilation**: All modules extend `Cross[moduleName](AllScalaVersions)` for Scala 2.13 and 3.3 support

Example:

```scala
object prometheus extends Cross[prometheus](AllScalaVersions)
trait prometheus extends KamonModule {
  override def artifactName = "kamon-prometheus"  // Preserves Maven artifact name
  // ...
}
```

## Dependency Management

### Centralized Dependencies (Deps Object)

All commonly used dependencies are defined in `build.mill` in the `Deps` object:

```scala
object Deps {
  // Versions - use `Version` suffix
  val okhttpVersion          = "4.12.0"
  val scalatestVersion       = "3.2.9"

  // Dependencies - NO suffix, just the library name
  def okhttp         = mvn"com.squareup.okhttp3:okhttp:$okhttpVersion"
  def scalatest      = mvn"org.scalatest::scalatest:$scalatestVersion"
}
```

**Naming Rules:**

- Version vals: `{libraryName}Version` (e.g., `okhttpVersion`, `scalatestVersion`)
- Dependency defs: Just the library name, no suffix (e.g., `okhttp`, `scalatest`, `logbackClassic`)

### Using Dependencies in Modules

Reference dependencies using `Deps.*`:

```scala
def mvnDeps = Seq(
  Deps.okhttp,
  Deps.scalatest
)
```

### Scala Version-Specific Dependencies

For libraries that don't publish Scala 3 artifacts, use conditional logic:

```scala
def mvnDeps = {
  val baseDeps = Seq(Deps.scalatest)
  if (crossValue == Scala2Version) {
    baseDeps :+ mvn"com.typesafe.akka::akka-http:10.1.8"
  } else {
    // Use Scala 2.13 artifact for Scala 3
    baseDeps :+ mvn"com.typesafe.akka:akka-http_2.13:10.1.8"
  }
}
```

## Shaded Dependencies

### When to Use Shading

Shade dependencies that could cause conflicts with user applications (e.g., okhttp, protobuf, internal utilities). Only shade
dependencies if there were shading configurations in the corresponding SBT project definition.

### Implementation Pattern

```scala
trait myModule extends KamonModule {
  // Dependencies to shade - these get bundled into the JAR
  def shadedMvnDeps = Seq(
    Deps.okhttp,
    Deps.protobuf
  )

  // Make shaded deps available at compile time
  def compileMvnDeps = shadedMvnDeps

  // Define relocation rules
  override def shadingRules = super.shadingRules ++ Seq(
    Rule.Relocate("okhttp3.**", "kamon.lib.@1"),
    Rule.Relocate("okio.**", "kamon.lib.@1"),
    Rule.Relocate("com.google.protobuf.**", "kamon.apm.shaded.@1"),
    Rule.ExcludePattern("META-INF/maven.*")
  )

  // Use shaded JAR as the published artifact
  override def jar: T[PathRef] = jarWithShadedDependencies
}
```

### Shading Conventions

- Use `kamon.lib.@1` for common libraries shared across modules
- Use `kamon.{module}.shaded.@1` for module-specific shading (e.g., `kamon.apm.shaded.@1`)
- Always include `Rule.ExcludePattern("META-INF/maven.*")` to avoid maven metadata conflicts

## Test Module Configuration

### Standard Test Module Pattern

```scala
object test extends SbtTests with TestModule.ScalaTest {
  def moduleDeps = super.moduleDeps ++ Seq(core.testkit(crossValue))

  def mvnDeps = Seq(
    Deps.scalatest,
    Deps.logbackClassic  // Optional, for logging in tests
  )
}
```

### Tests with Kanela Agent

Instrumentation modules require the Kanela agent attached to the JVM for tests to pass. Use the `TestWithKanelaAgent` trait (defined in `KamonModule`) instead of `SbtTests with TestModule.ScalaTest`:

```scala
object test extends TestWithKanelaAgent {
  def moduleDeps = super.moduleDeps ++ Seq(core.testkit(crossValue))

  def mvnDeps = Seq(
    Deps.scalatest,
    Deps.logbackClassic
  )
}
```

The `TestWithKanelaAgent` trait:
- Extends `SbtTests with TestModule.ScalaTest`
- Resolves the Kanela agent JAR from `Deps.kanelaAgent`
- Adds `-javaagent:/path/to/kanela-agent.jar` to `forkArgs`

Use this trait for any module in `instrumentation/` that has tests requiring bytecode instrumentation (context propagation, executor instrumentation, etc.).

### Important Notes

- Always include `core.testkit(crossValue)` in test `moduleDeps` - it provides jctools dependency needed at test runtime
- Use `SbtTests` trait for SBT-style test directory layout
- Use `TestModule.ScalaTest` for ScalaTest framework

## Migration Checklist

### For Each Module

1. **Create the Mill module structure**
   - Add module definition to appropriate `package.mill`
   - Set `artifactName` to preserve Maven artifact name
   - Configure `moduleDeps` to reference other Kamon modules

2. **Configure dependencies**
   - Use `Deps.*` for common dependencies
   - Set up `shadedMvnDeps`, `compileMvnDeps`, and `shadingRules` if shading is needed
   - Override `jar` to use `jarWithShadedDependencies` if shading

3. **Rename module directory**
   - Rename the entire directory from `reporters/kamon-{name}/` to `reporters/{name}/` (or `instrumentation/kamon-{name}/` to `instrumentation/{name}/`)
   - Use `mv` or `git mv` to preserve git history
   - Keep the SBT source layout (`src/main/scala`, etc.)
   - **IMPORTANT**: After verifying the migration works, delete the old `kamon-{name}` directory if you copied instead of renamed

4. **Configure test module**
   - Add test object with `SbtTests with TestModule.ScalaTest`
   - Include `core.testkit(crossValue)` in `moduleDeps`
   - For instrumentation modules: use `TestWithKanelaAgent` instead (includes Kanela agent for tests)

5. **Verify the migration**

   ```bash
   # Test for Scala 2.13
   ./mill 'reporters.{moduleName}[2.13.13].test'

   # Test for Scala 3
   ./mill 'reporters.{moduleName}[3.3.7].test'
   ```

6. **Check compilation resolves**

   ```bash
   ./mill resolve 'reporters.{moduleName}[__].compile'
   ```

## Reference: SBT to Mill Mapping

| SBT                                                      | Mill                                             |
| -------------------------------------------------------- | ------------------------------------------------ |
| `libraryDependencies += "org" %% "name" % "ver"`         | `mvn"org::name:ver"`                             |
| `libraryDependencies += "org" % "name" % "ver"`          | `mvn"org:name:ver"`                              |
| `libraryDependencies += "org" % "name" % "ver" % "test"` | Add to test module's `mvnDeps`                   |
| `dependsOn(otherProject)`                                | `def moduleDeps = Seq(other(crossValue))`        |
| `dependsOn(otherProject % "test")`                       | Add to test module's `moduleDeps`                |
| `assembly / assemblyShadeRules`                          | `def shadingRules` + `jarWithShadedDependencies` |
| `javaAgents += "org" % "name" % "ver" % "test"`          | Use `TestWithKanelaAgent` trait (for Kanela)     |
| `Test / fork := true` with javaagent                     | Override `forkArgs` in test module               |

## Common Issues and Solutions

### Issue: "object JavaConverters is deprecated"

This is a warning, not an error. The code still works but uses deprecated Scala 2.13 APIs.

### Issue: Mill internal classloader errors

Retry the command - these are transient Mill daemon issues, not actual build problems.

### Issue: Test can't find jctools classes

Ensure `core.testkit(crossValue)` is in the test module's `moduleDeps`.

### Issue: Scala 3 compilation fails with missing annotations

Add compile-time dependencies:

```scala
def compileMvnDeps = Seq(
  mvn"com.google.auto.value:auto-value-annotations:1.9"
)
```

### Issue: Testcontainers version incompatibility

If migrating a module that uses Testcontainers, ensure you're using version 2.0.3 or later to avoid Docker API compatibility issues. Testcontainers 2.0.0 introduced breaking changes:

**Required changes for Testcontainers 2.0+:**

1. **Update dependency names** - Modules are now prefixed with `testcontainers-`:
   ```scala
   // In Deps object (build.mill)
   def testContainersPostgres = mvn"org.testcontainers:testcontainers-postgresql:$testContainersVersion"
   def testContainersMysql    = mvn"org.testcontainers:testcontainers-mysql:$testContainersVersion"
   ```

2. **Update package imports** - Container classes moved to module-specific packages:
   ```scala
   // Before (1.x)
   import org.testcontainers.containers.PostgreSQLContainer
   import org.testcontainers.containers.MySQLContainer

   // After (2.0+)
   import org.testcontainers.postgresql.PostgreSQLContainer
   import org.testcontainers.mysql.MySQLContainer
   ```

3. **Remove core dependency** - Specific container modules transitively include the core module:
   ```scala
   // In test module mvnDeps - NO NEED to explicitly include testContainersCore
   def mvnDeps = Seq(
     Deps.testContainersPostgres,  // This transitively includes testcontainers core
     Deps.testContainersMysql
   )
   ```

**Note:** The core testcontainers artifact remains `org.testcontainers:testcontainers` (not `testcontainers-core`), but you typically don't need to depend on it directly.
