# Agent Guidance For Entio

This repository is currently in Phase 0B. Phase 0B exists to establish documentation and shared context for a future Kotlin/JVM Phase 1 Core Semantic Engine. It does not implement product behavior yet, and the actual Kotlin/JVM Gradle multi-module scaffold should be created later only by an explicit scaffold task. When implementation begins, this file should continue to guide agent behavior unless it is explicitly updated.

## Product Context

Entio helps teams build clean, trustworthy knowledge graphs from enterprise information.

The system should be ontology-first, meaning AI should work within approved concepts, relationships, and constraints instead of freely inventing graph structure. AI-generated graph or ontology changes should be treated as drafts that require deterministic validation and human review before becoming official.

## Current Rule For Agents

Do not create new modules, dependencies, or implementation code unless explicitly asked. During Phase 0B, do not add Kotlin/JVM Gradle scaffold files, module directories, source files, test files, build dependencies, or CI infrastructure unless an explicit scaffold task requests them.

When asked to implement code later, keep changes limited to the module or files named in the task.

If a task seems to require later-phase infrastructure, stop and explain why before implementing it.

## Current Scope

Only create and maintain initial documentation and architecture context unless explicitly asked otherwise.

Phase 1 is the Core Semantic Engine. It is intended to support:

- Loading an Entio project.
- Parsing small Turtle/RDF ontology files with existing libraries.
- Representing Entio-specific project objects, symbols, validation results, and graph diffs.
- Running basic validation checks.
- Generating semantic diffs.
- Exposing these capabilities through a simple CLI (Command Line Interface).

## Phase 1 Non-Goals

Do not add the following in Phase 1 unless the project direction changes explicitly:

- VS Code extension.
- Web app.
- Document ingestion.
- Autonomous AI agents.
- Schema RAG.
- Entity resolution.
- Stardog integration.
- Full FIBO indexing.
- A custom RDF, OWL, or SHACL framework.

## Software Architecture Rules

Follow these architecture rules unless the user explicitly updates the project direction.

### 1. Keep modules focused

Each module should have one clear responsibility.

- `core-types` defines shared Entio data objects.
- `semantic-engine` loads projects, resolves ontology sources, parses ontology files, and identifies symbols.
- `validation-engine` runs deterministic validation checks and produces validation reports.
- `graph-diff` compares graph states and produces semantic diffs.
- `cli` exposes engine capabilities through command-line commands.
- `shared` contains only generic utilities.

Do not put product logic in `shared`.

### 2. Preserve dependency direction

Lower-level modules should not depend on higher-level modules.

Allowed general direction:

- `cli` may depend on engine modules.
- engine modules may depend on `core-types` and `shared`.
- `core-types` should not depend on engine modules.
- `shared` should not depend on Entio product modules.

If a change requires reversing these dependencies, stop and explain why before implementing.

### 3. Keep the core independent from UI

Do not add VS Code, web app, or UI assumptions to the Kotlin core engine.

The core engine should produce structured outputs that future interfaces can consume.

### 4. Use existing semantic-web libraries

Do not implement custom RDF, OWL, SHACL, or Turtle parsing frameworks.

Use established libraries where appropriate. Entio-specific code should focus on project workflow, symbols, validation reports, semantic diffs, and reviewable change proposals.

### 5. Prefer deterministic validation

Validation logic must be repeatable.

The same project input should produce the same validation report. AI may assist later, but AI judgment should not determine whether a Phase 1 validation check passes.

### 6. Require specs and ExecPlans for meaningful features

For non-trivial features, follow:

Spec → ExecPlan → Implementation → Tests → Review

Do not implement broad features directly from a vague request.

### 7. Keep changes small

Prefer small, focused changes that can be reviewed and tested independently.

Do not implement multiple unrelated features in one change.

### 8. Add tests with implementation

Every implemented behavior should include focused tests in the relevant module.

Do not report a feature complete unless tests were added or updated and the verification commands were run.

### 9. Avoid speculative infrastructure

Do not add later-phase infrastructure unless explicitly requested.

This includes VS Code extension infrastructure, web app infrastructure, document ingestion, autonomous agents, Schema RAG, entity resolution, Stardog integration, and full FIBO indexing.

### 10. Document important architecture decisions

If a change makes an important architectural decision, add or update an ADR in `docs/decisions/`.

An ADR should explain the context, the decision, and the consequences.

### 11. Avoid unnecessary frameworks and abstractions

Do not add server frameworks, dependency injection frameworks, coroutine infrastructure, database layers, plugin systems, or broad abstraction layers unless a spec and ExecPlan explicitly justify them.

Prefer plain classes, simple functions, and clear module boundaries until complexity requires more infrastructure.

## Kotlin Engineering Rules

Follow these Kotlin rules unless the user explicitly updates the project direction.

### 1. Follow Kotlin coding conventions

Use official Kotlin coding conventions for naming, formatting, package structure, and file organization:

- Classes: UpperCamelCase
- Functions: lowerCamelCase()
- Properties: lowerCamelCase
- Packages: com.entio.lowercase

Prefer clear names over abbreviations.

### 2. Prefer immutable data

Use `data class` objects with `val` properties for Entio data models such as projects, ontology source references, loaded symbols, validation reports, semantic diffs, and change proposals.

Use `var` only when mutation is clearly required.

### 3. Model fixed states explicitly

Use `enum class` or `sealed class` / `sealed interface` for fixed sets of states such as validation severity, symbol kind, result status, and change proposal status.

Avoid representing important states as loose strings.

### 4. Use nullability intentionally

Use nullable types only when a value may truly be absent.

Avoid `!!` in production code. Handle missing values explicitly with safe calls, Elvis operators, or structured validation issues.

### 5. Keep public APIs explicit

Public module APIs should use explicit visibility and explicit return types.

Use `internal` for helpers that should not be part of a module’s public API.

### 6. Keep semantic-web library details contained

Use established RDF/OWL/SHACL libraries where appropriate, but keep their usage behind clear module boundaries.

Do not spread Jena, RDF4J, or OWL API types across unrelated modules unless explicitly required by the approved design.

### 7. Keep CLI thin

The CLI should route commands, call engine modules, and format output.

Do not put ontology parsing, validation rules, or graph diff logic directly in CLI files.

### 8. Add focused tests

Every implemented behavior should include focused tests in the relevant module.

Do not report implementation complete unless tests were added or updated and verification commands were run.

### 9. Use static analysis and formatting

When the Gradle setup supports it, use ktlint for formatting and detekt for Kotlin static analysis.

Do not bypass formatting or lint failures unless explicitly approved.

### 10. Avoid unnecessary frameworks

Do not add server frameworks, dependency injection frameworks, coroutine infrastructure, database layers, or plugin systems unless a spec and ExecPlan explicitly justify them.

## Documentation Style

- Be explicit about scope and non-goals.
- Use clear language for enterprise and semantic-web concepts.
- Distinguish trusted ontology rules from AI-generated draft changes.
- Favor concise architecture notes over broad speculative design.

## Git And Version Control Rules

Follow these Git rules unless the user explicitly instructs otherwise.

### 1. Inspect before changing Git state

Before staging, committing, or pushing, inspect the repository state.

Run:

```bash
git status --short
git branch --show-current
git remote -v
```

If the repository has uncommitted changes that were not made during the current task, stop and summarize them before proceeding.

Do not overwrite, discard, rebase, reset, or force-push user work unless explicitly instructed.

### 2. Use branches for meaningful changes

Do not commit directly to `main` after the initial repository setup unless explicitly instructed.

Use a descriptive branch name.

Examples:

```text
docs/kotlin-transition
docs/git-workflow
scaffold/kotlin-gradle
feature/project-loader
fix/validation-report-ordering
```

Branch names should be lowercase and use hyphens.

### 3. Keep commits focused

Each commit should represent one logical change.

Good examples:

```text
Add Kotlin engineering guidelines
Align Phase 1 docs with Kotlin JVM direction
Add Git workflow guidance for agents
Create Kotlin Gradle scaffold
Implement project config loading
```

Avoid vague commit messages such as:

```text
updates
fix stuff
changes
work
```

### 4. Review staged changes before committing

Before committing, run:

```bash
git diff --cached --stat
git diff --cached
```

Summarize what is staged.

Do not commit files that appear unrelated to the current task.

### 5. Keep `.gitignore` current

Before committing, check for generated files, dependency folders, local environment files, build outputs, secrets, editor files, operating system files, and logs.

Do not commit:

```text
node_modules/
.pnpm-store/
.gradle/
build/
*/build/
.env
.env.*
.DS_Store
.idea/
.vscode/
*.log
```

Do commit source files, documentation, examples, templates, build configuration, and intentionally included wrapper files.

When the Kotlin/JVM Gradle scaffold is created later, Gradle wrapper files should be committed:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

### 6. Do not commit secrets

Never commit credentials, API keys, tokens, private keys, passwords, or local environment files.

If a file appears to contain secrets, stop and ask the user before staging it.

### 7. Run verification before committing when applicable

For documentation-only changes, verification may be limited to reviewing diffs.

For Kotlin/Gradle changes, run the relevant commands when available:

```bash
./gradlew test
./gradlew build
./gradlew check
```

For future formatting or static analysis, run the configured checks when available.

### 8. Ask before pushing unless already authorized

Unless the user explicitly asked to push, prepare the commit and summarize:

- current branch
- files changed
- commit message
- verification commands run
- whether push is ready

If the user explicitly asked to push, push the current branch after committing.

### 9. Avoid force pushes

Do not use:

```bash
git push --force
git push --force-with-lease
```

unless explicitly instructed and the reason is clearly explained.

### 10. Summarize Git actions

After a commit or push, summarize:

- branch name
- commit hash
- commit message
- files changed
- verification results
- remote branch pushed
