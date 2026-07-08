# Kotlin Engine Guidelines

This document defines Kotlin engineering guidelines for Entio’s core engine code.

It is not tied to a specific development phase. These rules should guide Kotlin development across the project unless they are intentionally replaced by a later architecture decision record.

The purpose of this document is to make the Kotlin codebase understandable, testable, maintainable, and safe for real developers to extend over time.

## Core Principle

Entio’s Kotlin code should be boring, explicit, and easy to reason about.

Prefer:

- Clear names.
- Small classes.
- Small functions.
- Immutable data.
- Explicit states.
- Deterministic behavior.
- Focused tests.
- Narrow module responsibilities.

Avoid:

- Clever abstractions.
- Hidden side effects.
- Overly generic frameworks.
- Broad utility modules.
- Unnecessary inheritance.
- Logic spread across unrelated modules.

The goal is not to write impressive Kotlin. The goal is to write Kotlin that another developer can understand quickly and change safely.

---

## 1. Follow Kotlin Coding Conventions

All Kotlin code should follow official Kotlin coding conventions unless the project explicitly decides otherwise.

### Naming

Use clear, descriptive names.

```kotlin
class ProjectLoader

fun loadProject(projectRoot: Path): EntioResult<EntioProject>

val ontologySources: List<OntologySourceReference>
```

Use these conventions:

```text
Classes and interfaces: UpperCamelCase
Functions: lowerCamelCase()
Properties: lowerCamelCase
Constants: UPPER_SNAKE_CASE
Packages: lowercase dot-separated names
```

Examples:

```kotlin
package com.entio.semantic

class OntologyParser

fun parseOntology(source: OntologySourceReference): LoadedOntology
```

Avoid unclear abbreviations:

```kotlin
// Avoid
val cfg: ProjectConfig
val srcs: List<OntologySourceReference>
fun valProj()

// Prefer
val config: ProjectConfig
val ontologySources: List<OntologySourceReference>
fun validateProject()
```

### File Organization

A file should usually contain one primary class, interface, or concept.

Good:

```text
ProjectLoader.kt
OntologyParser.kt
ValidationReport.kt
SemanticDiff.kt
```

Avoid dumping unrelated types into one file just because they are small.

Acceptable exceptions:

- Small closely related enums.
- Small sealed-state definitions.
- Private helper types that are only used by the primary class in the file.

---

## 2. Prefer Immutable Data

Use immutable data by default.

Prefer:

```kotlin
data class ValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val message: String,
    val source: String?
)
```

Avoid mutable models unless mutation is truly required:

```kotlin
// Avoid unless necessary
data class ValidationIssue(
    var severity: ValidationSeverity,
    var message: String
)
```

Use `val` by default.

Use `var` only when:

- The value is intentionally stateful.
- Mutation is easier to understand than copying.
- The mutation is local and contained.
- The reason for mutation is obvious from the surrounding code.

For Entio data models, prefer immutable `data class` objects.

Good candidates for immutable data classes:

- `EntioProject`
- `OntologySourceReference`
- `LoadedSymbol`
- `LoadedOntology`
- `ValidationIssue`
- `ValidationReport`
- `SemanticDiff`
- `ChangeProposal`

Immutable objects are easier to test, diff, log, serialize, and reason about.

---

## 3. Use Data Classes For Data, Regular Classes For Behavior

Use `data class` when the object primarily represents data.

Example:

```kotlin
data class OntologySourceReference(
    val id: String,
    val path: Path,
    val format: OntologyFormat
)
```

Use a regular `class` when the object performs behavior or coordinates work.

Example:

```kotlin
class ProjectLoader(
    private val sourceResolver: OntologySourceResolver,
    private val ontologyParser: OntologyParser
) {
    fun loadProject(projectRoot: Path): EntioResult<LoadedEntioProject> {
        // implementation
    }
}
```

Rule of thumb:

```text
Data class = describes something.
Regular class = does something.
```

Avoid putting complex behavior inside data classes.

---

## 4. Model Fixed States Explicitly

Do not represent important state as loose strings.

Avoid:

```kotlin
val severity: String = "error"
val symbolKind: String = "class"
```

Prefer enums for simple fixed sets:

```kotlin
enum class ValidationSeverity {
    Error,
    Warning,
    Info
}
```

```kotlin
enum class SymbolKind {
    Class,
    Property,
    Individual,
    Shape,
    Namespace,
    Unknown
}
```

Use sealed classes or sealed interfaces when states carry different data.

Example:

```kotlin
sealed interface EntioResult<out T> {
    data class Success<T>(
        val value: T
    ) : EntioResult<T>

    data class Failure(
        val message: String,
        val issues: List<ValidationIssue> = emptyList(),
        val cause: Throwable? = null
    ) : EntioResult<Nothing>
}
```

This makes important states visible to the compiler and easier for developers to handle correctly.

---

## 5. Use Nullability Intentionally

Kotlin nullability should communicate meaning.

Use nullable types only when a value may truly be absent.

Good:

```kotlin
data class LoadedSymbol(
    val uri: String,
    val label: String?,
    val kind: SymbolKind
)
```

Here, `label: String?` clearly means a symbol may not have a human-readable label.

Avoid using nullable types as a substitute for unclear design:

```kotlin
// Avoid
val validationReport: ValidationReport?
```

Ask:

```text
Is the report truly optional?
Or should the function always return a report, even if it contains errors?
```

Avoid `!!` in production code.

```kotlin
// Avoid
val label = symbol.label!!
```

Prefer explicit handling:

```kotlin
val displayName = symbol.label ?: symbol.uri
```

Or return a validation issue:

```kotlin
if (symbol.label == null) {
    issues += ValidationIssue(
        severity = ValidationSeverity.Warning,
        code = "missing-label",
        message = "Symbol ${symbol.uri} does not have a label.",
        source = symbol.uri
    )
}
```

Rule:

```text
Nullable values should be handled deliberately, not forced.
```

---

## 6. Keep Public APIs Explicit

Entio’s Kotlin modules should be treated like internal libraries. Public APIs should be intentional.

For public classes and functions, use explicit return types.

Good:

```kotlin
class ProjectValidator {
    fun validate(project: LoadedEntioProject): ValidationReport {
        // implementation
    }
}
```

Avoid relying on inferred public return types:

```kotlin
// Avoid for public APIs
fun validate(project: LoadedEntioProject) = ...
```

Use `internal` for helpers that should not be part of a module’s public API.

Example:

```kotlin
internal fun normalizeOntologyPath(projectRoot: Path, rawPath: String): Path {
    // implementation
}
```

Rule:

```text
If another module should not call it directly, mark it internal.
```

This keeps module boundaries clean and prevents accidental coupling.

---

## 7. Keep Module Boundaries Clear

Each module should have a narrow responsibility.

Expected responsibilities:

```text
core-types
  Shared Entio data objects and enums.

semantic-engine
  Project loading, ontology source resolution, ontology parsing, and symbol extraction.

validation-engine
  Deterministic validation checks and validation report generation.

graph-diff
  Graph comparison and semantic diff generation.

cli
  Command routing, argument handling, and user-facing output.

shared
  Truly generic utilities only.
```

Do not put product logic in `shared`.

Good use of `shared`:

```text
Path helpers
Generic result wrapper
Small filesystem helpers
```

Bad use of `shared`:

```text
Ontology parsing
Validation rules
Symbol extraction
Diff logic
```

Rule:

```text
If the utility knows about Entio concepts, it probably does not belong in shared.
```

---

## 8. Preserve Dependency Direction

Lower-level modules should not depend on higher-level modules.

General direction:

```text
cli
  may depend on semantic-engine, validation-engine, graph-diff, core-types, shared

semantic-engine
  may depend on core-types and shared

validation-engine
  may depend on core-types, semantic-engine, and shared

graph-diff
  may depend on core-types and shared

core-types
  should not depend on engine modules

shared
  should not depend on Entio product modules
```

Avoid circular dependencies.

If a change requires a circular dependency, the design is probably wrong.

Preferred fix:

- Move shared data objects to `core-types`.
- Move generic helpers to `shared`.
- Move behavior to the module that owns the responsibility.
- Introduce a small interface only if there is a real dependency problem.

Do not introduce abstraction layers just to avoid thinking through module ownership.

---

## 9. Keep Semantic-Web Library Details Contained

Entio should rely on established RDF, OWL, SHACL, and Turtle libraries where appropriate.

However, third-party semantic-web library types should not spread unnecessarily across the whole codebase.

Good:

```text
OntologyParser uses Apache Jena, RDF4J, or OWL API internally.
LoadedOntology wraps the parsed result.
Other modules consume Entio-specific types where possible.
```

Avoid:

```text
CLI directly manipulates RDF models.
Validation rules depend deeply on parser-specific implementation details.
Graph diff logic is tightly coupled to one library without a clear boundary.
```

Rule:

```text
Use third-party libraries for standards-based graph mechanics.
Use Entio types for Entio workflow concepts.
```

Examples of Entio workflow concepts:

- Project configuration.
- Ontology source references.
- Loaded symbols.
- Validation reports.
- Semantic diffs.
- Human-reviewable change proposals.

This keeps Entio portable and easier to refactor if a semantic-web library changes or is replaced later.

---

## 10. Keep CLI Code Thin

The CLI should not contain core engine logic.

CLI responsibilities:

- Parse command-line arguments.
- Call the appropriate engine service.
- Format output for terminal users.
- Return appropriate process exit codes.

CLI should not:

- Parse Turtle/RDF directly.
- Implement validation rules.
- Compute semantic diffs.
- Contain ontology business logic.
- Know details of third-party RDF library internals.

Good:

```kotlin
class Commands(
    private val projectLoader: ProjectLoader,
    private val projectValidator: ProjectValidator
) {
    fun validate(projectRoot: Path): Int {
        val loadedProject = projectLoader.loadProject(projectRoot)
        val report = projectValidator.validate(loadedProject)
        println(formatValidationReport(report))
        return if (report.hasErrors) 1 else 0
    }
}
```

Bad:

```kotlin
// Avoid
fun validateCommand(path: String) {
    // Reads YAML
    // Parses RDF
    // Extracts symbols
    // Runs validation logic
    // Formats output
}
```

Rule:

```text
CLI wires the system together. Engine modules do the work.
```

---

## 11. Prefer Small Functions

Functions should do one thing clearly.

Good:

```kotlin
fun resolveOntologySources(config: EntioConfig): List<ResolvedOntologySource>

fun parseOntology(source: ResolvedOntologySource): LoadedOntology

fun extractSymbols(ontology: LoadedOntology): List<LoadedSymbol>
```

Avoid functions that combine unrelated steps:

```kotlin
// Avoid
fun loadAndValidateAndPrintProject(path: Path) {
    // too much responsibility
}
```

If a function becomes hard to name, it is probably doing too much.

Rule:

```text
A good function name should describe the whole function without using "and".
```

---

## 12. Prefer Clear Control Flow Over Clever Kotlin

Kotlin has many expressive language features. Use them when they improve clarity, not just because they are available.

Good use of `when`:

```kotlin
val exitCode = when (report.status) {
    ValidationStatus.Valid -> 0
    ValidationStatus.Invalid -> 1
}
```

Good use of Elvis operator:

```kotlin
val displayName = symbol.label ?: symbol.uri
```

Avoid dense chains when they obscure behavior:

```kotlin
// Avoid if hard to read
val result = sources
    .mapNotNull { it.takeIf { s -> s.enabled }?.let(parser::parse) }
    .flatMap(SymbolExtractor::extract)
    .groupBy { it.kind }
    .mapValues { it.value.sortedBy { symbol -> symbol.uri } }
```

Prefer intermediate names when it improves readability:

```kotlin
val enabledSources = sources.filter { it.enabled }
val loadedOntologies = enabledSources.map(parser::parse)
val symbols = loadedOntologies.flatMap(symbolExtractor::extract)
val symbolsByKind = symbols.groupBy { it.kind }
```

Rule:

```text
Readable code is better than compact code.
```

---

## 13. Use Extension Functions Sparingly

Extension functions can make code cleaner, but they can also hide behavior.

Good use:

```kotlin
internal fun Path.existsAsFile(): Boolean =
    Files.exists(this) && Files.isRegularFile(this)
```

Avoid extension functions that contain major product behavior:

```kotlin
// Avoid
fun Path.loadAndValidateEntioProject(): ValidationReport
```

Rule:

```text
Use extension functions for small convenience behavior, not core workflow behavior.
```

Core workflow should live in named classes such as:

- `ProjectLoader`
- `OntologyParser`
- `ProjectValidator`
- `GraphDiffer`

---

## 14. Error Handling Should Be Structured

Avoid returning raw strings for important errors.

Avoid:

```kotlin
fun validate(project: EntioProject): List<String>
```

Prefer structured errors:

```kotlin
data class ValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val message: String,
    val source: String?
)
```

Use consistent result types for operations that can fail.

Example:

```kotlin
sealed interface EntioResult<out T> {
    data class Success<T>(val value: T) : EntioResult<T>
    data class Failure(
        val message: String,
        val issues: List<ValidationIssue> = emptyList(),
        val cause: Throwable? = null
    ) : EntioResult<Nothing>
}
```

Use exceptions for truly exceptional or unexpected failures.

Use structured validation issues for expected user/project problems, such as:

- Missing `entio.yaml`.
- Missing ontology file.
- Invalid path.
- Unsupported ontology format.
- Parse failure.
- Duplicate ontology source ID.

Rule:

```text
Expected project problems should become validation issues, not unstructured crashes.
```

---

## 15. Validation Must Be Deterministic

Validation behavior should be repeatable.

The same input should produce the same validation report.

Avoid validation logic that depends on:

- LLM judgment.
- Network calls.
- Current time.
- Randomness.
- Unstable ordering.
- Machine-specific paths in user-facing messages.

When producing validation reports, sort issues where practical.

Example:

```kotlin
val sortedIssues = issues.sortedWith(
    compareBy<ValidationIssue> { it.severity.ordinal }
        .thenBy { it.code }
        .thenBy { it.source ?: "" }
        .thenBy { it.message }
)
```

Rule:

```text
Validation output should be stable enough to test and review.
```

---

## 16. Semantic Diffs Should Be Stable And Reviewable

Semantic diff output should be designed for human review.

A diff should clearly distinguish:

- Added items.
- Removed items.
- Modified items.
- Warnings or uncertainty.
- Source graph/version where relevant.

Avoid producing diffs that depend on unordered collection iteration.

Prefer sorted output for readability and stable tests.

Example concepts:

```kotlin
data class SemanticDiff(
    val addedSymbols: List<LoadedSymbol>,
    val removedSymbols: List<LoadedSymbol>,
    val addedRelationships: List<GraphRelationship>,
    val removedRelationships: List<GraphRelationship>
)
```

Rule:

```text
Diffs are product outputs, not just internal debug information.
```

---

## 17. Avoid Over-Engineering

Do not add frameworks or abstraction layers before they are justified.

Avoid by default:

- Server frameworks.
- Dependency injection frameworks.
- Coroutine infrastructure.
- Database layers.
- Plugin systems.
- Reflection-heavy designs.
- Event buses.
- Complex inheritance hierarchies.
- Generic repository/service/controller patterns.
- Premature caching layers.

Prefer:

- Plain Kotlin classes.
- Constructor injection without a framework.
- Small functions.
- Direct dependencies.
- Focused modules.
- Simple test fixtures.

Good:

```kotlin
class ProjectLoader(
    private val sourceResolver: OntologySourceResolver,
    private val ontologyParser: OntologyParser
)
```

Unnecessary:

```kotlin
class ProjectLoaderServiceFactoryProviderRegistry
```

Rule:

```text
Do not design for imagined future complexity. Design for the current known responsibility while keeping boundaries clean.
```

---

## 18. Use Constructor Injection Without A Framework

When a class depends on another component, pass the dependency through the constructor.

Good:

```kotlin
class ProjectValidator(
    private val rules: List<ValidationRule>
)
```

This makes dependencies obvious and easy to test.

Avoid hidden dependencies:

```kotlin
// Avoid
object GlobalValidator {
    val rules = loadRulesFromSomewhere()
}
```

Avoid dependency injection frameworks unless a spec and ExecPlan explicitly justify them.

Rule:

```text
Dependencies should be visible from the constructor.
```

---

## 19. Avoid Global Mutable State

Global mutable state makes tests flaky and behavior harder to reason about.

Avoid:

```kotlin
object EntioState {
    var currentProject: EntioProject? = null
}
```

Prefer passing state explicitly:

```kotlin
fun validate(project: LoadedEntioProject): ValidationReport
```

Acceptable global constants:

```kotlin
const val DEFAULT_CONFIG_FILE_NAME = "entio.yaml"
```

Rule:

```text
Global constants are fine. Global mutable state is not.
```

---

## 20. Logging And Output Should Be Intentional

Core engine modules should return structured results.

They should not print directly to the terminal.

Avoid:

```kotlin
class ProjectValidator {
    fun validate(project: LoadedEntioProject) {
        println("Validation failed")
    }
}
```

Prefer:

```kotlin
class ProjectValidator {
    fun validate(project: LoadedEntioProject): ValidationReport {
        // return structured result
    }
}
```

The CLI can decide how to display results.

Rule:

```text
Engine modules return data. Interfaces display data.
```

This keeps the core reusable by CLI, VS Code, web, APIs, and tests.

---

## 21. Tests Should Be Focused And Readable

Every implemented behavior should have tests.

Test names should describe behavior.

Good:

```kotlin
@Test
fun `loads project from entio yaml`() {
    // test
}

@Test
fun `returns validation issue when ontology file is missing`() {
    // test
}
```

Avoid vague names:

```kotlin
@Test
fun test1() {
    // test
}
```

Each test should generally follow:

```text
Arrange
Act
Assert
```

Example:

```kotlin
@Test
fun `returns issue when ontology source path does not exist`() {
    val project = EntioProject(
        name = "example",
        ontologySources = listOf(
            OntologySourceReference(
                id = "missing",
                path = Path.of("ontology/missing.ttl"),
                format = OntologyFormat.Turtle
            )
        )
    )

    val report = validator.validate(project)

    assertTrue(report.hasErrors)
    assertTrue(report.issues.any { it.code == "ontology-file-not-found" })
}
```

Rule:

```text
Tests should explain the expected behavior of the system.
```

---

## 22. Test Public Behavior, Not Private Implementation

Prefer testing public behavior.

Avoid making private helper functions public just to test them.

Good:

```kotlin
@Test
fun `extracts classes from turtle ontology`() {
    val ontology = parser.parse(sampleOntology)
    val symbols = symbolExtractor.extractSymbols(ontology)

    assertTrue(symbols.any { it.kind == SymbolKind.Class && it.uri.endsWith("Loan") })
}
```

Avoid:

```kotlin
@Test
fun `tests private regex helper`() {
    // brittle implementation test
}
```

If a private helper becomes complex enough that it needs direct testing, consider whether it should be moved into a small internal class with a clear responsibility.

Rule:

```text
Tests should protect behavior, not freeze implementation details.
```

---

## 23. Use Small Test Fixtures

Prefer small, understandable fixtures.

Good:

```text
examples/simple-ontology/ontology/simple.ttl
```

Avoid using large ontology files for normal unit tests.

Large ontologies may be useful for integration or performance testing later, but everyday tests should be fast and easy to debug.

Rule:

```text
Small tests should fail clearly.
```

---

## 24. Formatting And Static Analysis

The project should use automated formatting and static analysis once the build supports it.

Recommended tools:

```text
ktlint
  Formatting and Kotlin style checks.

detekt
  Static analysis and code smell detection.
```

These checks should eventually run in CI alongside tests.

Recommended verification commands once configured:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew test
./gradlew build
```

Do not bypass formatting or static analysis failures unless explicitly approved.

Rule:

```text
Style and quality checks should be automated, not debated manually.
```

---

## 25. Documentation In Code

Use comments sparingly.

Prefer clear names and simple structure over comments explaining confusing code.

Good comments explain why something exists.

```kotlin
// Sort validation issues to keep CLI output and tests deterministic.
val sortedIssues = issues.sortedWith(issueComparator)
```

Avoid comments that repeat what the code says.

```kotlin
// Avoid: increments count by one
count += 1
```

Use KDoc for public APIs when the purpose is not obvious.

Example:

```kotlin
/**
 * Loads an Entio project from a project root directory.
 *
 * This function reads project configuration, resolves ontology sources,
 * parses ontology files, and returns a loaded project model.
 */
fun loadProject(projectRoot: Path): EntioResult<LoadedEntioProject>
```

Rule:

```text
Comment intent, not mechanics.
```

---

## 26. Serialization Boundaries Should Be Explicit

When Entio objects need to be serialized to JSON or another format, keep serialization decisions explicit.

Avoid mixing serialization concerns deeply into core logic.

Good:

```text
core-types defines stable data structures.
cli or adapter layer decides how to serialize output.
```

If serialization annotations are needed, they should be introduced intentionally and consistently.

Rule:

```text
Core models should remain understandable as domain models, not become accidental transport hacks.
```

---

## 27. Prefer Composition Over Inheritance

Use inheritance only when there is a clear type hierarchy.

Prefer composition for behavior reuse.

Good:

```kotlin
class ProjectLoader(
    private val sourceResolver: OntologySourceResolver,
    private val ontologyParser: OntologyParser,
    private val symbolExtractor: SymbolExtractor
)
```

Avoid deep inheritance:

```kotlin
// Avoid unless strongly justified
abstract class BaseLoader
class ProjectLoader : BaseLoader()
class RemoteProjectLoader : ProjectLoader()
```

Rule:

```text
Most Entio behavior should be composed from small classes, not inherited from base classes.
```

---

## 28. Avoid Premature Performance Optimizations

Write clear code first.

Optimize only when there is evidence.

Acceptable reasons to optimize:

- A measured bottleneck.
- A known large ontology use case.
- A performance test showing a real issue.
- A spec or architecture decision requiring it.

Avoid:

- Caches without invalidation rules.
- Parallelism without need.
- Coroutines for simple file loading.
- Complex indexing before requirements are proven.

Rule:

```text
Correct, clear, deterministic code comes before optimization.
```

---

## 29. Review Checklist For Kotlin Changes

Before considering a Kotlin change complete, check:

```text
[ ] Does the code follow the owning module’s responsibility?
[ ] Are public APIs explicit?
[ ] Are data models immutable where practical?
[ ] Are fixed states modeled with enums or sealed types?
[ ] Are nullable values handled deliberately?
[ ] Is there any avoidable use of `!!`?
[ ] Is CLI logic thin?
[ ] Are third-party semantic-web library types contained appropriately?
[ ] Are validation and diff outputs deterministic?
[ ] Are functions small and clearly named?
[ ] Is there unnecessary framework or abstraction usage?
[ ] Were focused tests added or updated?
[ ] Were verification commands run?
```

---

## 30. When To Update This Document

Update this document when the project intentionally changes Kotlin engineering standards.

Examples:

- The project adopts a formatter or linter.
- The project enables explicit API mode.
- The project chooses a preferred RDF/OWL library boundary.
- The project changes module dependency rules.
- The project introduces a justified framework.

Do not update this document for one-off implementation details.

When a change affects architecture, add or update an ADR in `docs/decisions/`.
```