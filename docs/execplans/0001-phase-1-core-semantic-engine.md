# ExecPlan: Phase 1 Core Semantic Engine

## Status

Completed

## Source Spec

[Feature Spec: Phase 1 Core Semantic Engine](../specs/0001-phase-1-core-semantic-engine.md)

## Objective

Implement the Phase 1 Core Semantic Engine as a Kotlin/JVM Gradle multi-module project with small, testable slices.

The implementation should support project config loading, ontology source resolution, Turtle/RDF parsing through an established JVM library, symbol extraction, deterministic validation reports, semantic diffs, and thin CLI commands.

This plan must not be implemented until approved.

## Scope

This plan covers the future Kotlin/JVM Gradle implementation of the Phase 1 Core Semantic Engine. It includes the target module structure, affected files, recommended dependencies, core types/classes/functions, CLI commands, test fixtures, implementation slices, and multi-agent safety rules.

### Target Module Structure

- `core-types`: shared Entio data objects, enums, sealed results, validation and diff models.
- `semantic-engine`: project loading, ontology source resolution, Turtle/RDF parsing, and symbol extraction.
- `validation-engine`: deterministic checks and validation report generation.
- `graph-diff`: graph comparison and diff formatting.
- `cli`: thin command-line wrapper.
- `shared`: generic utilities only.

### Affected Modules And Files

Expected future files:

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/wrapper/`
- `core-types/build.gradle.kts`
- `semantic-engine/build.gradle.kts`
- `validation-engine/build.gradle.kts`
- `graph-diff/build.gradle.kts`
- `cli/build.gradle.kts`
- `shared/build.gradle.kts`
- `core-types/src/main/kotlin/com/entio/core/...`
- `semantic-engine/src/main/kotlin/com/entio/semantic/...`
- `validation-engine/src/main/kotlin/com/entio/validation/...`
- `graph-diff/src/main/kotlin/com/entio/diff/...`
- `cli/src/main/kotlin/com/entio/cli/...`
- `shared/src/main/kotlin/com/entio/shared/...`
- matching test directories under each module.

Files that should not be changed during feature slices unless explicitly approved:

- `AGENTS.md`
- `README.md`
- `docs/architecture/`
- `docs/decisions/`
- `docs/specs/`
- `docs/execplans/`

### Recommended Dependencies

- Kotlin/JVM Gradle plugin: required for Kotlin compilation.
- JUnit 5 or Kotlin test: required for focused unit and integration tests.
- SnakeYAML Engine: recommended for parsing `entio.yaml`; mature JVM YAML parser, avoids hand-written YAML parsing.
- Apache Jena: recommended initial RDF/Turtle library because it is mature, widely used, supports Turtle parsing, RDF models, namespaces, and later SHACL-related workflows.
- Clikt or Picocli: recommended for CLI parsing. Picocli is mature and Java-friendly; Clikt is Kotlin-friendly. Prefer one lightweight CLI library, not a framework.
- Optional later: RDF4J evaluation if Jena proves too broad or if RDF4J APIs fit Entio better.

Do not add server frameworks, dependency injection frameworks, database layers, coroutine infrastructure, plugin systems, or semantic-web libraries unrelated to approved Phase 1 behavior.

### Types, Classes, And Functions To Introduce

`core-types`:

- `data class EntioProjectConfig`
- `data class EntioProject`
- `data class OntologySourceReference`
- `data class ResolvedOntologySource`
- `enum class OntologyFormat`
- `data class LoadedOntology`
- `data class LoadedSymbol`
- `enum class SymbolKind`
- `data class Iri`
- `enum class ValidationSeverity`
- `data class ValidationIssue`
- `data class ValidationReport`
- `enum class ValidationStatus`
- `data class GraphTriple`
- `data class GraphState`
- `data class SemanticDiff`
- `data class SemanticDiffEntry`
- `enum class SemanticDiffKind`
- `data class ChangeProposal`
- `enum class ChangeProposalStatus`
- `sealed interface EntioResult<out T>`

`semantic-engine`:

- `class ProjectConfigLoader`
- `fun loadConfig(projectRoot: Path): EntioResult<EntioProjectConfig>`
- `class OntologySourceResolver`
- `fun resolveSources(projectRoot: Path, config: EntioProjectConfig): EntioResult<List<ResolvedOntologySource>>`
- `class OntologyParser`
- `fun parse(source: ResolvedOntologySource): EntioResult<LoadedOntology>`
- `class SymbolExtractor`
- `fun extractSymbols(ontology: LoadedOntology): List<LoadedSymbol>`
- `class ProjectLoader`
- `fun loadProject(projectRoot: Path): EntioResult<EntioProject>`

`validation-engine`:

- `class ProjectValidator`
- `fun validateProject(projectRoot: Path): ValidationReport`
- `class ValidationIssueSorter`

`graph-diff`:

- `class GraphDiffer`
- `fun diff(before: GraphState, after: GraphState): SemanticDiff`
- `class SemanticDiffFormatter`

`cli`:

- `class EntioCli`
- `class ValidateCommand`
- `class SymbolsCommand`
- `class DiffCommand`
- `fun main(args: Array<String>)`

`shared`:

- path normalization helpers.
- stable sorting helpers only if generic.
- no Entio product logic.

### CLI Commands To Introduce

- `entio validate <project-root>`
- `entio symbols <project-root>`
- `entio diff <before-project-root> <after-project-root>`

Optional CLI flags:

- `--format text|json`
- `--quiet`

Do not introduce server mode, watch mode, UI commands, document ingestion commands, or AI commands in Phase 1.

### Test Fixtures

Use `examples/simple-ontology` for an end-to-end happy path once the scaffold exists.

Add module-local fixtures for:

- valid `entio.yaml`.
- invalid YAML.
- missing ontology file.
- duplicate source IDs.
- unsafe path.
- valid Turtle.
- invalid Turtle.
- before/after graph pairs for diff tests.

Fixtures should be small, deterministic, and committed with tests.

## Non-Goals

- Do not add server frameworks, dependency injection frameworks, database layers, coroutine infrastructure, plugin systems, or semantic-web libraries unrelated to approved Phase 1 behavior.
- Do not introduce server mode, watch mode, UI commands, document ingestion commands, or AI commands in Phase 1.
- Do not change `AGENTS.md`, `README.md`, `docs/architecture/`, `docs/decisions/`, `docs/specs/`, or `docs/execplans/` during implementation slices unless explicitly approved.
- Do not put Entio product logic in `shared`.
- Do not implement custom Turtle parsing.
- Do not introduce reasoning, entity resolution, full domain ontology indexing, visual UI, human approval workflow, or OWL reasoning diff behavior.
- Do not let the CLI own ontology parsing, validation rules, or diff computation.

## Steps

### Implementation Sequence

1. Kotlin/JVM Gradle scaffold, including an empty/minimal `shared` module.
2. `core-types`.
3. Project config loading.
4. Ontology source resolution.
5. Turtle/RDF parsing.
6. Loaded symbol extraction.
7. Validation report generation.
8. Semantic diff generation.
9. CLI commands.
10. End-to-end example project test.
11. Add or extract `shared` utilities only when a concrete repeated need exists.

This order keeps shared contracts stable before engine behavior depends on them. The `shared` module exists early as part of the scaffold, but it should not be filled with speculative utilities.

### Dependency Order

- Kotlin/JVM Gradle scaffold comes first.
- `core-types` comes before all engine modules and should remain self-contained.
- The `shared` module may exist from the scaffold, but actual utilities should only be added when needed by an approved implementation slice.
- `core-types` should not depend on `shared`.
- Engine modules may depend on `core-types`.
- Engine modules may depend on `shared` only for concrete generic utilities that already exist or are introduced by the current approved slice.
- `semantic-engine` comes before `validation-engine`, `graph-diff`, and `cli` rely on loaded project or parsed ontology behavior.
- `validation-engine` comes after project loading, source resolution, and parsing APIs exist.
- `graph-diff` comes after graph state and triple models exist.
- `cli` comes after reusable module APIs exist.
- End-to-end tests come after CLI and engine modules are available.

### Shared Module Policy

The `shared` module should not be filled speculatively.

A utility may be added to `shared` only if all of the following are true:

1. The utility is needed by the current approved implementation slice.
2. The utility is generic and does not use Entio product terminology.
3. The utility is likely to be reused by more than one module, or the ExecPlan explicitly approves placing it in `shared`.
4. The utility has focused tests.

If a helper is only used by one module, keep it in that module.

If a helper uses Entio concepts, place it in `core-types` or the owning engine module instead.

If no concrete shared utility is needed, the `shared` module should remain minimal and buildable.

### Implementation Slices

### 1. Kotlin/JVM Gradle Scaffold

Goal: Create the Gradle multi-module shell.

Allowed files/modules:

- root Gradle files.
- Gradle wrapper.
- empty module directories.
- module `build.gradle.kts` files.
- minimal placeholder tests only if needed to prove the scaffold.

Forbidden actions/modules:

- no product behavior.
- no RDF parsing.
- no CLI commands.
- no edits to specs or ExecPlans unless scope changes.

Dependencies:

- Kotlin/JVM Gradle plugin.
- test dependency only.

Expected output:

- Six modules compile:
  - `core-types`
  - `semantic-engine`
  - `validation-engine`
  - `graph-diff`
  - `cli`
  - `shared`
- `shared` exists as an empty/minimal buildable module.
- `./gradlew test`, `./gradlew build`, and `./gradlew check` work.

Tests:

- placeholder module smoke tests only if needed to prove the scaffold.

Verification commands:

- `./gradlew test`
- `./gradlew build`
- `./gradlew check`

Stop conditions:

- Gradle structure requires changing approved module names.
- dependency setup requires network or tooling decisions not covered by the plan.
- scaffold work begins adding product behavior.

### 2. `core-types`

Goal: Introduce stable shared Entio data contracts.

Allowed files/modules:

- `core-types`.
- tests for data construction, enum values, and simple helper behavior if local to `core-types`.

Forbidden actions/modules:

- no engine behavior.
- no YAML parsing.
- no RDF library dependencies.
- no CLI.
- no dependency on `shared`.

Dependencies:

- Kotlin standard library.
- test library.

Expected output:

- Immutable data objects and fixed-state enums/sealed interfaces.
- `core-types` remains self-contained.
- No dependency from `core-types` to `shared`.

Tests:

- object construction.
- equality.
- enum/sealed-state coverage.
- validation report status helpers if included.

Verification commands:

- `./gradlew :core-types:test`
- `./gradlew check`

Stop conditions:

- contracts are unclear enough that downstream modules would guess.
- implementing the slice requires dependencies on engine modules or `shared`.
- data objects start taking on behavior that belongs in an engine module.

### 3. Project Config Loading

Goal: Load and parse `entio.yaml` into `EntioProjectConfig`.

Allowed files/modules:

- `semantic-engine`.
- `core-types` only if minor contract adjustments are needed.
- `shared` only if a generic utility is immediately needed and satisfies the Shared Module Policy.

Forbidden actions/modules:

- no RDF parsing.
- no validation-engine rules beyond structured load failures.
- no CLI behavior.
- no speculative shared utilities.

Dependencies:

- SnakeYAML Engine.

Expected output:

- `ProjectConfigLoader.loadConfig(projectRoot)` or equivalent.
- Structured success/failure behavior using approved core result/report types.
- Tests for supported config-loading cases.

Tests:

- valid config.
- missing file.
- invalid YAML.
- missing required fields.

Verification commands:

- `./gradlew :semantic-engine:test`
- `./gradlew check`

Stop conditions:

- required config shape conflicts with the approved spec.
- config loading requires broad validation behavior that belongs in `validation-engine`.
- a proposed shared helper is only used by `semantic-engine`.

### 4. Ontology Source Resolution

Goal: Resolve ontology source references safely relative to project root.

Allowed files/modules:

- `semantic-engine`.
- `core-types` only if minor contract adjustments are needed.
- `shared` only if a generic path helper is immediately needed and satisfies the Shared Module Policy.

Forbidden actions/modules:

- no RDF parsing.
- no validation-engine behavior.
- no CLI.
- no speculative shared utilities.

Dependencies:

- none beyond existing modules unless explicitly justified.

Expected output:

- `OntologySourceResolver.resolveSources(...)` or equivalent.
- Safe local path resolution.
- Rejection of unsafe paths.
- Stable source resolution output.

Tests:

- existing relative file.
- missing file.
- absolute path rejection.
- path traversal rejection.
- duplicate source ID handling if not already covered.

Verification commands:

- `./gradlew :semantic-engine:test`
- `./gradlew check`

Stop conditions:

- path rules need product decision beyond local file safety.
- shared path helpers would only be used by this one module.
- path resolution begins doing ontology parsing or validation-engine work.

### 5. Turtle/RDF Parsing

Goal: Parse Turtle/RDF files using an established JVM semantic-web library.

Allowed files/modules:

- `semantic-engine`.
- `core-types` only if minor loaded ontology or graph model adjustments are needed.

Forbidden actions/modules:

- no custom Turtle parser.
- no validation-engine rules beyond parse result issues.
- no CLI.
- no broad exposure of parser-specific types across unrelated modules.

Dependencies:

- Apache Jena recommended, unless the ExecPlan or user explicitly chooses RDF4J instead.

Expected output:

- `OntologyParser.parse(source)` or equivalent.
- `LoadedOntology` wrapping or referencing parsed RDF data through a contained boundary.
- Parser behavior that supports later symbol extraction and graph diffing.

Tests:

- valid Turtle parses.
- invalid Turtle returns structured failure.
- parser output is deterministic enough for symbol extraction.

Verification commands:

- `./gradlew :semantic-engine:test`
- `./gradlew check`

Stop conditions:

- RDF library API exposure leaks across module boundaries in a way that conflicts with `core-types`.
- implementation starts building custom RDF/Turtle parsing behavior.
- parser behavior requires full OWL reasoning.

### 6. Loaded Symbol Extraction

Goal: Extract a stable list of basic ontology symbols.

Allowed files/modules:

- `semantic-engine`.
- `core-types` only for symbol model adjustments.

Forbidden actions/modules:

- no reasoning engine.
- no entity resolution.
- no full FIBO indexing.
- no CLI.
- no validation-engine behavior.

Dependencies:

- RDF library already selected.

Expected output:

- `SymbolExtractor.extractSymbols(ontology)` or equivalent.
- Stable list of loaded symbols.
- Basic symbol kinds such as classes, properties, individuals, shapes, namespaces, and unknown terms as approved by the spec.

Tests:

- extract classes.
- extract properties.
- extract individuals if in approved scope.
- extract labels from `rdfs:label`.
- stable ordering.

Verification commands:

- `./gradlew :semantic-engine:test`
- `./gradlew check`

Stop conditions:

- symbol scope expands beyond Phase 1 without approval.
- extraction requires reasoning or external lookup.
- symbol extraction starts doing validation-engine work.

### 7. Validation Report Generation

Goal: Produce deterministic validation reports across config, source, parse, and symbol checks.

Allowed files/modules:

- `validation-engine`.
- `core-types` only for validation model adjustments.
- `semantic-engine` only through existing public APIs.

Forbidden actions/modules:

- no CLI formatting logic.
- no AI judgment.
- no network checks.
- no semantic-engine internals unless already exposed by approved APIs.

Dependencies:

- `core-types`.
- `semantic-engine`.

Expected output:

- `ProjectValidator.validateProject(projectRoot)` or equivalent.
- Deterministic validation report output.
- Stable issue codes and severity handling.

Tests:

- all spec validation cases.
- deterministic ordering.
- severity and issue code checks.
- expected project problems become structured validation issues.

Verification commands:

- `./gradlew :validation-engine:test`
- `./gradlew check`

Stop conditions:

- validation checks require non-deterministic behavior.
- validation requires changing unstable contracts in `core-types`.
- validation starts formatting CLI output directly.

### 8. Semantic Diff Generation

Goal: Compare graph states and produce stable semantic diffs.

Allowed files/modules:

- `graph-diff`.
- `core-types` only for diff model adjustments.
- `semantic-engine` only through approved graph/loaded ontology APIs if dependency direction remains approved.

Forbidden actions/modules:

- no visual UI.
- no human approval workflow.
- no OWL reasoning diff.
- no CLI behavior.
- no parser-specific leakage across unrelated modules.

Dependencies:

- `core-types`.
- possibly approved public graph model or adapter APIs from `semantic-engine`.

Expected output:

- `GraphDiffer.diff(before, after)` or equivalent.
- stable `SemanticDiff`.
- deterministic ordering of diff entries.

Tests:

- identical graph returns empty diff.
- added triple.
- removed triple.
- stable ordering.
- label change if implemented.

Verification commands:

- `./gradlew :graph-diff:test`
- `./gradlew check`

Stop conditions:

- diffing needs parser-specific types exposed broadly.
- diff behavior expands into full OWL reasoning.
- diff implementation starts creating visual or approval workflow behavior.

### 9. CLI Commands

Goal: Add thin CLI commands over reusable modules.

Allowed files/modules:

- `cli`.

Forbidden actions/modules:

- no ontology parsing logic in CLI.
- no validation rules in CLI.
- no diff computation in CLI.
- no UI/web/server behavior.
- no core model changes unless explicitly approved.

Dependencies:

- Picocli or Clikt.
- `semantic-engine`.
- `validation-engine`.
- `graph-diff`.
- `core-types`.

Expected output:

- `entio validate`.
- `entio symbols`.
- `entio diff`.
- CLI routes commands to reusable module APIs.
- CLI exits with stable status codes.

Tests:

- command parsing.
- exit code behavior.
- basic output snapshots or structured output tests.
- CLI smoke tests.

Verification commands:

- `./gradlew :cli:test`
- `./gradlew check`

Stop conditions:

- CLI starts owning core logic.
- CLI requires web, UI, server, or VS Code assumptions.
- command behavior conflicts with the approved spec.

### 10. End-To-End Example Project Test

Goal: Verify the full Phase 1 path against `examples/simple-ontology`.

Allowed files/modules:

- integration tests in the appropriate module.
- example fixture updates only if fixture is invalid.
- `examples/simple-ontology`.

Forbidden actions/modules:

- no new product surfaces.
- no broad fixture corpus.
- no unrelated refactors.

Dependencies:

- existing Phase 1 modules.

Expected output:

- example project validates successfully.
- symbols can be listed.
- diff can be generated for controlled before/after fixture.

Tests:

- one happy-path example test.
- one small invalid project test.

Verification commands:

- `./gradlew test`
- `./gradlew build`
- `./gradlew check`

Stop conditions:

- example requires behavior outside Phase 1.
- test requires creating a large fixture corpus.
- test requires unstable behavior or non-deterministic output.

### 11. Add Or Extract `shared` Utilities When Needed

Goal: Add truly generic shared utilities only when a concrete repeated need exists.

Allowed files/modules:

- `shared`.
- tests for introduced shared utilities.
- the current approved slice that needs the utility, if applicable.

Forbidden actions/modules:

- no ontology parsing.
- no validation rules.
- no symbol extraction.
- no diff logic.
- no Entio-specific models.
- no broad utility collections.
- no speculative helpers.

Dependencies:

- Kotlin standard library unless otherwise explicitly justified.

Expected output:

- only concrete generic utilities needed by the approved slice.
- focused tests for each introduced utility.
- no Entio product terminology in `shared`.

Tests:

- tests for each utility that is actually added.
- no tests for hypothetical helpers.

Verification commands:

- `./gradlew :shared:test`
- `./gradlew check`

Stop conditions:

- utility is only needed by one module.
- utility uses Entio product terminology.
- utility is speculative.
- utility requires new dependencies not approved by the current slice.

## Multi-Agent Safety

### Must Be Serial

The following work must be done serially because it affects shared contracts, project rules, dependency choices, or module boundaries:

- Specs.
- ExecPlans.
- `AGENTS.md`.
- Build files.
- `core-types`.
- Dependency selection.
- Module boundary changes.
- New shared contracts or changes to existing shared utility behavior.

These areas define assumptions that other modules may depend on. Parallel changes here can cause agents to build against conflicting contracts.

### May Be Parallelized Later After Contracts Stabilize

The following work may be parallelized later, but only after relevant contracts are stable and each agent has a clearly bounded file scope:

- Validation checks in `validation-engine`.
- Graph diff cases in `graph-diff`.
- CLI command formatting in `cli`.
- `semantic-engine` parser tests and symbol extraction tests, if the parser and symbol contracts are stable.
- End-to-end fixture additions, if expected behavior is already defined.
- Tests for existing `shared` utilities, if the utility behavior is already stable.
- Small shared utility additions only if explicitly approved by the current slice and they satisfy the Shared Module Policy.

Parallel work must not modify unstable shared contracts such as:

- `core-types`
- specs
- ExecPlans
- build files
- `AGENTS.md`
- shared utility behavior that other active slices depend on

If a parallel task requires changing one of these shared areas, the agent must stop and ask before continuing.

### Risks

- RDF library types may leak across modules and make future refactoring harder.
- Project config shape may need revision after early implementation feedback.
- Semantic diff expectations may expand beyond basic graph additions and removals.
- CLI output format may become unstable if text output is not specified carefully.
- Validation issue ordering may become flaky without explicit sorting rules.
- Jena may be heavier than needed for Phase 1, though its maturity is useful.

## Verification

Expected full Phase 1 verification commands after scaffold creation:

```sh
./gradlew test
./gradlew build
./gradlew check
```

Module-level commands should be used during slices:

```sh
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew :cli:test
./gradlew :shared:test
```

### Definition Of Done

- All planned modules exist.
- Module dependencies follow the approved direction.
- `core-types` contains stable Entio workflow objects.
- Project config loading works for the approved `entio.yaml` shape.
- Ontology sources resolve safely.
- Turtle/RDF parsing uses an established JVM semantic-web library.
- Loaded symbols can be extracted from small ontology files.
- Validation reports are deterministic.
- Semantic diffs are deterministic and human-reviewable.
- CLI commands are thin wrappers over module APIs.
- Example project tests pass.
- Full verification commands pass.
- Phase 1 non-goals remain excluded.
- No custom RDF, OWL, SHACL, or Turtle framework exists.

## Rollback Notes

Each slice should be independently revertible.

Rollback strategy:

- Revert the specific slice commit.
- Keep previously approved contracts intact unless the slice changed them.
- If a dependency causes issues, revert the dependency and the slice that introduced it together.
- Do not roll back unrelated docs or later approved slices.
