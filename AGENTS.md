# Agent Guidance For Entio

This repository now contains the completed Phase 1 Kotlin/JVM Core Semantic Engine foundation, the completed Phase 1.5 Core Semantic Engine Stabilization work, the completed Phase 2 Controlled Ontology Editing Workbench foundation, the completed Phase 2.5+ workbench usability and staged-change work, and the completed Phase 3 Semantic Description Layer. Earlier planning documents remain for history; the repository is no longer documentation-only.

Phase 1 is intentionally small: it supports local Entio project configuration, small Turtle/RDF ontology parsing, basic symbol extraction, deterministic validation reports, semantic graph diffs, and a thin CLI. Later product surfaces and enterprise features are still out of scope unless explicitly requested.

Phase 2, Phase 2.5, Phase 2.5+, and Phase 3 are complete. Phase 4 planning covers OWL reasoning and SHACL constraint authoring and validation; no Phase 4 implementation has begun. Phase 5 planning covers external ontology browsing and Schema RAG as a later phase. The Kotlin semantic engine remains the source of truth for RDF and ontology behavior, while the VS Code layer delegates semantic work to it.

## Product Context

Entio helps teams build clean, trustworthy knowledge graphs from enterprise information.

The system should be ontology-first, meaning AI should work within approved concepts, relationships, and constraints instead of freely inventing graph structure. AI-generated graph or ontology changes should be treated as drafts that require deterministic validation and human review before becoming official.

## Current Rule For Agents

Do not create new modules, dependencies, or implementation code unless explicitly asked.

When asked to implement code, keep changes limited to the module or files named in the task.

If a task seems to require later-phase infrastructure, stop and explain why before implementing it.

## Current Scope

Phase 1, Phase 1.5, Phase 2, Phase 2.5, Phase 2.5+, and Phase 3 are complete. The current Entio foundation supports:

- Loading an Entio project.
- Parsing small Turtle/RDF ontology files with existing libraries.
- Representing Entio-specific project objects, symbols, validation results, and graph diffs.
- Running basic validation checks.
- Generating semantic diffs.
- Exposing these capabilities through a simple CLI (Command Line Interface).
- Representing controlled graph changes, atomic change sets, previews, proposals, baselines, apply results, and rollback results.
- Translating supported typed ontology edits into graph changes.
- Validating proposal previews, checking semantic equivalence, and applying current approved proposals atomically.
- Exposing machine-readable proposal preview, validation, diff, apply, and reject commands.
- Providing a VS Code ontology workbench for project browsing, label-first selection, deterministic IRI display, supported typed editing, deletion dependency review, multi-edit staging, combined preview, approval, rejection, refresh, and opening changed sources.
- Building deterministic semantic descriptors for classes, properties, annotation properties, and individuals.
- Selecting preferred labels, collecting alternate labels and definitions, and preserving explicit semantic annotations.
- Performing deterministic semantic search with stable match reasons and ranked results.
- Translating supported semantic edits through the existing proposal, validation, diff, approval, apply, reload, and rollback workflow.

Current implementation notes:

- The Gradle modules are `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, and `shared`.
- The CLI exposes `validate`, `symbols`, and `diff`.
- The CLI also exposes `project-summary`, `proposal-preview`, `proposal-validate`, `proposal-diff`, `proposal-apply`, `proposal-reject`, structured label/IRI/deletion operations, and combined proposal operations.
- `semantic-engine` exposes reusable project loading through `ProjectLoader`.
- `EntioProject` is constructed as a loaded-project aggregate.
- RDF graph terms preserve IRI resources, blank nodes, plain literals, datatyped literals, and language-tagged literals.
- `ProposalApplier` writes only the target ontology source and restores it when post-save verification fails.
- The VS Code extension delegates ontology operations to the Kotlin CLI and does not write RDF directly.
- `shared` intentionally remains minimal and should not collect speculative utilities.

Phase 2.5 and Phase 2.5+ implemented:

- User-facing typed edits for properties, individuals, assertions, values, hierarchy, domains, ranges, and labels through the existing proposal workflow.
- Label-first entity resolution and deterministic collision-checked IRI generation.
- Explicit deletion dependency analysis and review for supported symbols.
- In-memory multi-edit staging, combined semantic preview, validation, approval, rejection, atomic application, reload, and rollback coverage.
- A copied-fixture regression path that verifies source preservation before approval and recovery after failed verification.

Phase 3 implemented:

- Explicit semantic descriptors for classes, object properties, datatype properties, annotation properties, and individuals.
- Deterministic preferred-label selection, alternate-label and definition extraction, annotation handling, and semantic ordering.
- Semantic metadata validation and typed semantic edit translation.
- Machine-readable descriptor and search commands.
- VS Code semantic details, label-aware search, semantic edit forms, and staged semantic previews.
- Copied-fixture regression coverage for semantic descriptors, search, semantic edits, approval, rejection, reload, and rollback.

Phase 4 is the current planning boundary for OWL reasoning and SHACL constraint authoring and validation. It is not implemented yet. Phase 5 planning for external ontology browsing and Schema RAG is later and is not part of the current implementation.

Phase 2 implemented:

- Adding controlled graph changes and atomic change sets.
- Translating supported typed ontology edits into graph changes in the Kotlin engine.
- Creating preview graphs without mutating source files.
- Generating semantic diffs and validation reports before approval.
- Applying only approved and current proposals to the correct Turtle source.
- Restoring the previous source and graph state when save or verification fails.
- Introducing a minimal VS Code workbench that delegates semantic behavior to the Kotlin engine.
- Treating the proposal workflow as git-like by analogy only: draft, preview, diff, review, approve, and apply.

## Phase 2 Through Phase 2.5+ Historical Non-Goals

Do not add the following in Phase 2 unless the project direction changes explicitly:

- Web app.
- Document ingestion.
- Autonomous AI agents.
- Schema RAG.
- Entity resolution across documents or external sources. Local deterministic label resolution is part of Phase 2.5+.
- Full domain ontology indexing.
- A custom RDF, OWL, or SHACL framework.
- LLM-generated ontology edits.
- Production graph storage.
- Full Protégé feature parity.
- Full OWL class-expression editing.
- Full SHACL authoring or validation environment.
- Long-term project version history.
- Durable staged-session or proposal persistence.
- Git staging, commits, pushes, branch management, or pull-request creation inside Entio.
- OWL reasoning or full SHACL validation.

## Current Phase Boundary

Phase 3 is complete. Do not treat the Phase 4 or Phase 5 planning documents as implemented behavior.

Phase 4 implementation must remain limited to an approved plan for OWL reasoning, import-aware reasoning views, SHACL authoring, SHACL validation, and their existing proposal-workflow integration. Phase 5 implementation must remain deferred until explicitly activated and planned.

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

Follow these Kotlin rules unless the user explicitly updates the project direction. For more detailed explanations, examples, and further guidelines, refer to docs/architecture/003-kotlin-engine-guidelines.md

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

### 11. Comment and document code intentionally

Prefer clear names and simple structure over excessive comments.

Use comments to explain why code exists, not to restate what the code does.

Good comments explain:

- Non-obvious design decisions.
- Important constraints.
- Deterministic ordering requirements.
- Safety checks.
- Library boundary decisions.
- Temporary workarounds with a clear reason.

Avoid comments that merely repeat the code.

Use KDoc for public classes, public functions, and public data objects when their purpose, contract, or usage is not obvious from the name.

Do not leave commented-out code in the repository.

If a TODO is necessary, include the reason and expected follow-up.

Good Example:

```kotlin
// Sort issues to keep validation output stable across repeated runs.
val sortedIssues = issues.sortedWith(issueComparator)
```

Bad Example:

```kotlin
// Sort the issues.
val sortedIssues = issues.sortedWith(issueComparator)
```

## Documentation Style

- Be explicit about scope and non-goals.
- Use clear language for enterprise and semantic-web concepts.
- Distinguish trusted ontology rules from AI-generated draft changes.
- Favor concise architecture notes over broad speculative design.
- Prefer comments and KDoc that explain intent, constraints, and tradeoffs rather than restating the code.

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

### 2. Confirm local base is current before starting implementation

Before creating a branch for a meaningful change, check whether the local base branch is up to date with the remote.

Run:

```bash
git fetch --prune origin
git status -sb
```

If the current task should start from `main`, switch to `main` and update it with a fast-forward pull:

```bash
git switch main
git pull --ff-only origin main
```

If the local branch has diverged from the remote, or if updating would require a merge, rebase, reset, or force operation, stop and ask the user before continuing.

Do not start a new implementation branch from an outdated local base unless the user explicitly instructs otherwise.

### 3. Use branches for meaningful changes

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

### 4. Keep commits focused

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

### 5. Review staged changes before committing

Before committing, run:

```bash
git diff --cached --stat
git diff --cached
```

Summarize what is staged.

Do not commit files that appear unrelated to the current task.

### 6. Keep `.gitignore` current

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

### 7. Do not commit secrets

Never commit credentials, API keys, tokens, private keys, passwords, or local environment files.

If a file appears to contain secrets, stop and ask the user before staging it.

### 8. Run verification before committing when applicable

For documentation-only changes, verification may be limited to reviewing diffs.

For Kotlin/Gradle changes, run the relevant commands when available:

```bash
./gradlew test
./gradlew build
./gradlew check
```

For future formatting or static analysis, run the configured checks when available.

### 9. Ask before pushing unless already authorized

Unless the user explicitly asked to push, prepare the commit and summarize:

- current branch
- files changed
- commit message
- verification commands run
- whether push is ready

If the user explicitly asked to push, push the current branch after committing.

### 10. Avoid force pushes

Do not use:

```bash
git push --force
git push --force-with-lease
```

unless explicitly instructed and the reason is clearly explained.

### 11. Summarize Git actions

After a commit or push, summarize:

- branch name
- commit hash
- commit message
- files changed
- verification results
- remote branch pushed
