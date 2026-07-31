# ExecPlan: Phase 12 Ontology-Grounded Document Analysis

## Status

Approved for implementation.

The Phase 12 scope and spec are approved. This document is authoritative for
dependency order, allowed files, slice completion artifacts, verification
commands, and stop conditions. Phase 12 is not yet implemented.

## Goal

Replace the ontology-blind beginning of the current document-analysis path with
an ontology-grounded path:

```text
verified extracted text
→ deterministic local candidate extraction
→ deterministic retrieval from authorized ontology scopes
→ bounded model interpretation over evidence and retrieved choices
→ Kotlin evidence, selection, structure, and freshness verification
→ existing deterministic semantic compilation
→ connected editable human review
→ existing proposal, approval, apply, reload, rollback, and provenance
```

The implementation must not add embeddings, a vector database, a second
ontology index, automatic approval, or a new ontology write path.

## Related Spec

- [Phase 12 scope](../architecture/phase-12-scope.md)
- [Phase 12 feature spec](../specs/0023-phase-12-ontology-grounded-document-analysis.md)
- [Current document analysis and adaptive recovery](../architecture/document-analysis-and-adaptive-recovery.md)
- [Phase 11 spec](../specs/0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md)
- [Phase 11.5+ spec](../specs/0022-phase-11.5-plus-deterministic-compilation-of-connected-document-models.md)
- [Phase 11.5+ ExecPlan](0022-phase-11.5-plus-deterministic-compilation-of-connected-document-models.md)

## Objective

Deliver a production document-analysis path where:

- local natural-language processing creates stable evidence-linked candidates;
- every retained candidate is searched across all authorized Entio scopes before
  semantic modeling;
- the selected model chooses reuse, extension, new creation, or unresolved
  treatment using server-issued retrieval IDs;
- Kotlin verifies every choice and compiles only supported meaning;
- missing reviewer-solvable context becomes editable `NeedsInput` state;
- prerequisites stay attached to the edits they support;
- review remains compact, collapsible, editable, and human-controlled;
- existing safe proposal and apply behavior remains unchanged.

## Current State

The implementation audit found the following current behavior.

### Active production sequence

`DocumentIngestionOrchestrator` currently performs:

```text
extraction
→ one ontology-blind discovery call per document
→ connected-model calls and optional consolidation
→ focused prerequisite completion
→ current ontology snapshot
→ deterministic semantic assembly
→ deterministic compilation and verification
→ grouped review
```

Legacy reconciliation, ontology-alignment, critic, and low-level final-planning
contracts remain in the codebase, but the streamlined production path does not
call them for new tasks.

### Existing search and matching services

- `SemanticDescriptionService` provides deterministic label, alternate-label,
  IRI, annotation, and asserted-type search over local and imported descriptors.
- `FiboSchemaSearchService` provides deterministic search and context scoring
  over the pinned approved FIBO catalog.
- `DocumentOntologyMatcher` provides bounded matching over applied, imported,
  current-work, same-task, retained-provenance, and curated-FIBO records.
- `DocumentIngestionOrchestrator` already builds applied, imported, and retained
  provenance records after model interpretation.
- The orchestrator contains a lexical `ontologyContext` helper, but the active
  flow does not use it as a complete pre-model retrieval boundary.

### Existing compilation and review

- `DocumentSemanticPlanCompiler` and `DocumentChangeSetPlanVerifier` compile and
  verify supported semantic meaning.
- `DocumentRecommendationDraftTranslator`, `DocumentIngestionWebService`, and
  `StagingWorkflowService` reuse the existing typed draft and proposal path.
- `DocumentReviewWorkspace` exposes grouped recommendations and operation-level
  edits. Existing matching uses canonical IRIs rather than Phase 12 selection
  IDs.
- `DocumentIngestionWorkspace.tsx` already renders connected recommendations as
  collapsible `<details>` cards, but it does not yet expose the full Phase 12
  grounded-choice and prerequisite-edit contract.
- Public progress messages do not consistently separate candidate, model-item,
  recommendation, and expanded-edit counts.

### Existing limits and lifecycle

- Intake, document count, document size, page, extracted-text, OCR, provider
  response, timeout, retry, and concurrency safeguards already exist.
- The current provider path uses bounded response item counts and a global
  logical-call budget.
- One draft batch remains bounded to 20 typed edits.
- Current contracts still contain historical task-wide candidate or discovery
  bounds and UI fields such as `maximumAcceptedEdits`.
- The current architecture document states that valid modeled items,
  recommendations, and edits must not be silently discarded by a task-wide
  semantic ceiling.
- Uploads, extraction artifacts, incomplete tasks, prompts, responses, and review
  workspaces remain temporary; only applied-document provenance is durable.

### Existing permanent fixtures

- `examples/simple-ontology/documents/consumer-lending-servicing-compliance-standard.pdf`
- `examples/simple-ontology/documents/commercial-account-and-payment-authorization-policy.pdf`
- `web-server/src/test/resources/document-ingestion/phase-11.5-two-pdf-expectations.json`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentSemanticProviderBenchmarkTest.kt`

## Target State

- Candidate extraction runs locally and deterministically before any semantic
  model call.
- Candidate records have stable IDs, categories, normalized text, exact evidence
  spans, nearby relationship hints, and pinned contract/resource versions.
- A focused retrieval service coordinates existing Entio search and matching
  services without storing another ontology index.
- Retrieval covers applied local, imports, private draft, shared staging,
  proposal, same-task candidates, retained provenance, and pinned FIBO scopes.
- At most 20 deterministically ranked choices per candidate enter the prompt;
  full-state duplicate and no-op checks remain unbounded by that prompt limit.
- Grounded requests contain compact evidence, related candidates, and returned
  choices. The model has no retrieval tool.
- Grounded results use `ReuseExisting`, `ExtendExisting`, `ProposeNew`, or
  `Unresolved`, with server-issued selection IDs for reuse and extension.
- Model-supplemented candidates receive evidence verification and deterministic
  retrieval before any new-entity recommendation can compile.
- Kotlin verifies all selections, fingerprints, kinds, source permissions,
  domains, ranges, datatypes, types, assertions, duplicates, dependencies, and
  coverage.
- `NeedsInput` represents reviewer-solvable missing semantic context.
- Existing semantic compilation remains the only document-to-typed-edit
  compiler.
- Connected review exposes editable grounded choices and prerequisites while the
  browser remains unable to decide validity.
- Counts for evidence blocks, NLP candidates, grounded items, recommendations,
  and expanded edits remain distinct.
- Valid work is chunked and staged in bounded batches rather than discarded or
  made review-only because of a task-wide semantic ceiling.
- Existing approval, apply, reload, rollback, and provenance behavior remains
  the only write path.

## Scope

Implementation is limited to Phase 12 document analysis, its neutral contracts,
the existing semantic retrieval and compiler boundaries, the web review surface,
tests, benchmark fixtures, and phase documentation.

No slice may implement an unrelated assistant capability, new ontology catalog,
new persistence system, or new client surface.

## Non-Goals

This plan does not implement:

- embeddings, vector search, or a vector database;
- a second ontology index, graph database, or external search service;
- a custom general-purpose NLP framework;
- non-English document analysis;
- new ontology catalogs beyond project imports and pinned FIBO;
- automatic approval, automatic apply, or model-written ontology sources;
- raw RDF, Turtle, SPARQL, or low-level edit planning by the model;
- a second compiler, proposal workflow, apply workflow, or provenance store;
- durable document, prompt, response, task, or review persistence;
- autonomous agents or model-controlled tools;
- unsupported OWL or SHACL meanings;
- CLI or VS Code document-ingestion features;
- unrelated assistant, reasoning, graph-visualization, collaboration, or
  production identity work.

## Affected Modules And Files

Expected production changes are limited to the following paths. Before creating
any proposed new file, search for an existing file with the same responsibility
and extend it instead if one exists.

### `core-types`

- new focused provider-neutral contracts, preferably
  `core-types/src/main/kotlin/com/entio/core/DocumentGroundedAnalysisContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
  for Phase 12 version constants, stages, recommendation status, and compatible
  semantic-plan references
- `core-types/src/main/kotlin/com/entio/core/DocumentIngestionContracts.kt` only
  for additive progress/count contracts if those cannot live in the focused
  Phase 12 file
- `core-types/src/main/kotlin/com/entio/core/DocumentRecommendationContracts.kt`
  only when an existing public recommendation contract must be reused rather
  than duplicated
- matching tests under `core-types/src/test/kotlin/com/entio/core/`

### `semantic-engine`

- new focused retrieval coordination, preferably
  `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyRetrievalService.kt`
- new grounded-result verification, preferably
  `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentGroundedAnalysisVerifier.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyMatcher.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/SemanticDescriptionService.kt`
  only if a small reusable query capability is missing
- `semantic-engine/src/main/kotlin/com/entio/semantic/FiboSchemaSearchService.kt`
  only if a small reusable bounded-result adapter is missing
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentSemanticPlanCompiler.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentChangeSetPlanVerifier.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentCompletenessMetricService.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentRecommendationDraftTranslator.kt`
  only for compatible Phase 12 result translation
- matching tests under `semantic-engine/src/test/kotlin/com/entio/semantic/`

The retrieval service receives current-work and provenance records as explicit
inputs. The semantic engine must not depend on web-server stores.

### `web-server`

- `web-server/build.gradle.kts` for the one audited local NLP dependency and its
  pinned English resource artifacts
- new local extraction service, preferably
  `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentCandidateExtractionService.kt`
- new grounded orchestration service and provider-neutral request adapter,
  preferably
  `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentGroundedAnalysisService.kt`
- new read-only context builder if needed, preferably
  `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentRetrievalContextFactory.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
  only for shared provider budgets, failure classification, or compatibility
  adapters that cannot move to the focused Phase 12 service
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestrator.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionConfiguration.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionTaskManager.kt`
  only for additive count/progress storage
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentReviewWorkspace.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionWebService.kt`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt` only for a
  read-only current-work snapshot or bounded document-draft batching
- `web-server/src/main/kotlin/com/entio/web/Application.kt` only if additive
  constructor wiring or versioned serialization requires it
- matching tests under `web-server/src/test/kotlin/com/entio/web/` and
  `web-server/src/test/kotlin/com/entio/web/ingestion/`
- Phase 12 additions under
  `web-server/src/test/resources/document-ingestion/`

Any NLP resources must come from the audited dependency/resource mechanism.
Do not create copied or renamed model binaries when a pinned dependency artifact
provides them.

### `web-app`

- `web-app/src/web/projectApi.ts`
- `web-app/src/web/projectApi.test.ts`
- `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.tsx`
- `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.test.tsx`
- `web-app/src/styles.css`
- `web-app/e2e/document-ingestion.spec.ts`

### Documentation and completion records

- `docs/decisions/phase-12-slice-0-contract-and-dependency-audit.md`
- one exact completion artifact for each later slice as listed below
- `docs/architecture/document-analysis-and-adaptive-recovery.md`
- `docs/architecture/ai-subsystem-map.md`
- `docs/architecture/repository-structure-and-code-architecture.md`
- `docs/architecture/phase-12-scope.md`
- `docs/specs/0023-phase-12-ontology-grounded-document-analysis.md`
- this ExecPlan
- `docs/phase-summaries/phase-12-summary.md`
- `README.md`
- `AGENTS.md`

Any production file outside this list requires an approved amendment to the
scope, spec, and ExecPlan before it changes.

## Modules And Areas That Must Not Change

- `cli` and `vscode-extension` gain no document-ingestion capability.
- `shared` receives no Entio product logic.
- `validation-engine` and `graph-diff` are reused through existing APIs. A
  required production change in either module stops the slice and requires a
  plan amendment.
- Intake, file parsing, PDF extraction, DOCX extraction, OCR, temporary storage,
  provider credential storage, model discovery, model selection, and the general
  ontology assistant remain unchanged.
- The pinned FIBO package and generated indexes remain immutable.
- No new Gradle module, server framework, dependency-injection framework,
  database, queue, vector store, embedding model, search server, ontology index,
  external ontology catalog, or autonomous agent runtime may be added.
- No raw RDF, Turtle, SPARQL, low-level Entio operation DTO, or source-write
  instruction may enter a provider response contract.
- No new proposal, staging, approval, apply, reload, rollback, or provenance
  workflow may be created.
- No production document, prompt, response, or task persistence may be added.

## Pinned Phase 12 Contracts And Bounds

Slice 0 must record the exact constant names and versions before implementation.
The version family must use `phase-12-...-v1` and cover:

- candidate extraction and pinned NLP resources;
- ontology retrieval query, ranking, and result;
- grounded prompt, request, and response;
- grounded verification result;
- public review and progress/count contracts;
- benchmark manifest and scoring.

The following product rules are already settled:

| Rule | Phase 12 value |
| --- | --- |
| Prompt-visible ontology choices | At most 20 per document candidate |
| Supported language | English only |
| Model selection | Current user's verified explicit selection; no silent fallback |
| Draft staging batch | At most 20 typed edits per internal batch |
| Provider retry and response safety | Preserve bounded existing behavior |
| Ontology scopes | Applied, imported, private draft, shared staging, proposal, same task, retained provenance, pinned FIBO |
| Write authority | Existing explicit human approval only |

Per-request and emergency resource safeguards remain required. They must fail
with an explicit incomplete-work result when complete processing cannot
continue. They must not:

- silently truncate candidates or grounded items;
- claim complete coverage after truncation;
- convert overflow into fake review-only findings;
- impose a product-level task-wide ceiling on valid recommendations or typed
  edits.

The existing 20-expanded-edit recommendation boundary may be retained only as
an atomic compilation boundary with deterministic dependency-safe splitting.
It cannot become a task-wide edit ceiling. Slice 0 must identify every current
task-wide ceiling and classify it as:

- a resource safeguard that fails visibly;
- a per-request/provider bound;
- a per-atomic-batch bound; or
- a product ceiling that Phase 12 must retire.

## Slice Execution Discipline

Implementation must follow the dependency order below. Each slice is one
independent implementation unit with:

- its own branch from the accumulated local base;
- one focused implementation scope;
- focused tests and verification;
- the exact completion artifact listed for that slice;
- one focused commit;
- a pushed remote slice branch when push is authorized;
- a clean local non-fast-forward merge into the accumulated base before the next
  slice begins.

Do not implement slices in parallel. Do not combine slices into one branch or
commit. If a merge is not clean, stop before resolving it. Do not push the base
branch unless explicitly authorized.

Before every new file, search for the same purpose and use the exact path in this
plan. If ownership is unclear, stop rather than creating a duplicate.

## Dependency Order

```text
Slice 0 contract and dependency audit
→ Slice 1 provider-neutral Phase 12 contracts
→ Slice 2 deterministic local candidate extraction
→ Slice 3 deterministic ontology retrieval
→ Slice 4 grounded provider boundary
→ Slice 5 production orchestration and work key
→ Slice 6 deterministic verification and compilation integration
→ Slice 7 connected editable review and bounded draft batching
→ Slice 8 offline regression, security, and controlled provider benchmark
→ Slice 9 documentation and phase completion
```

Later slices may use only behavior completed by earlier slices. A later slice
must not rewrite an earlier approved contract merely to make its implementation
easier.

## Implementation Slices

### Slice 0: Freeze The Baseline And Resolve Contracts

#### Branch

`docs/phase-12-contract-audit`

#### Goal

Record the current reliable baseline and resolve every open dependency,
contract, current-work, compact-context, limit, and benchmark question before
production code changes.

#### Allowed Files And Modules

- read-only inspection across the repository;
- `docs/decisions/phase-12-slice-0-contract-and-dependency-audit.md`;
- the Phase 12 scope, spec, and ExecPlan only for an explicitly approved
  clarification discovered by the audit;
- temporary local benchmark output outside the repository or ignored by Git.

#### Forbidden Actions And Modules

- no production or test-code changes;
- no dependency or NLP resource installation in the repository;
- no copied benchmark fixture, document, model, or ontology asset;
- no change to Phase 12 product scope without explicit approval;
- no provider credential, prompt, response, or benchmark output committed.

#### Expected Changes Or Output

The audit record must settle:

- the exact established JVM NLP library and pinned English resource artifacts;
- license, checksum or artifact coordinates, distribution size, startup cost,
  the repository's Java 21 target and supported runtime compatibility,
  offline-test behavior, and deterministic configuration;
- whether resource artifacts come from Maven dependencies or one intentional
  repository asset;
- all current discovery, connected-model, prerequisite, semantic assembly,
  compiler, review, progress, and draft producers and consumers;
- the exact active Phase 11.5+ path to replace for new tasks and legacy contracts
  to retain temporarily;
- the exact read-only source for private draft, shared staging, proposal,
  same-task, retained provenance, and pinned FIBO records;
- whether `StagingWorkflowService`, `DocumentReviewWorkspaceStore`, and current
  record builders already expose enough data or need one narrow additive read
  method;
- compact retrieval fields for every entity kind;
- deterministic query normalization, score normalization, scope ordering,
  duplicate removal, tie-breaking, and selection-ID derivation;
- exact `phase-12-...-v1` contract and version names;
- the public review/progress compatibility strategy and whether `apiVersion`
  remains `v1` additively or requires `v2`;
- every task-wide, per-response, per-batch, retry, and emergency safeguard and
  its Phase 12 treatment;
- the work-key inputs and stale-state rules;
- the Phase 12 two-document benchmark ontology state, expected top-20 matches,
  positive/negative expectations, and scoring;
- exact focused and full verification commands;
- baseline results for existing deterministic suites and one controlled current
  benchmark when a credential is explicitly available.

#### Completion Artifact

`docs/decisions/phase-12-slice-0-contract-and-dependency-audit.md`

#### Tests

- Run existing Phase 11.5+ deterministic core, semantic, server, and review
  tests without changing them.
- Confirm the two permanent PDFs and existing expectation manifest are present
  exactly once.
- Run the existing controlled provider benchmark only when explicitly enabled
  with a verified credential and exact model ID.

#### Verification Commands

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :web-server:test --tests '*DocumentAnalysis*' --tests '*DocumentIngestion*' --tests '*DocumentReview*'
npm --prefix web-app test -- --run DocumentIngestionWorkspace
git diff --check
```

#### Stop Conditions

- Stop if no established local JVM NLP library satisfies license, Java,
  packaging, English coverage, and deterministic fixture requirements.
- Stop if required current-work retrieval needs a new persistence layer or
  semantic-engine dependency on web-server.
- Stop if the retrieval contract requires embeddings, a vector store, a second
  ontology index, or an external hosted service.
- Stop if the existing compiler cannot accept a verified mapping to existing
  semantic-plan references without a second compiler.
- Stop if baseline deterministic tests fail for reasons not already documented.
- Stop and amend the planning documents before any scope expansion.

### Slice 1: Add Provider-Neutral Phase 12 Contracts

#### Branch

`feature/phase-12-grounded-contracts`

#### Goal

Add immutable, provider-neutral contracts for candidate extraction, ontology
retrieval, grounded decisions, count reporting, `NeedsInput`, and frozen Phase
12 work identity before adding behavior.

#### Allowed Files And Modules

- `core-types/src/main/kotlin/com/entio/core/DocumentGroundedAnalysisContracts.kt`;
- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`;
- `core-types/src/main/kotlin/com/entio/core/DocumentIngestionContracts.kt` only
  when required by the Slice 0 compatibility decision;
- `core-types/src/main/kotlin/com/entio/core/DocumentRecommendationContracts.kt`
  only to extend an existing exact responsibility;
- `core-types/src/test/kotlin/com/entio/core/DocumentGroundedAnalysisContractsTest.kt`;
- existing core contract tests only when an additive contract changes them;
- `docs/decisions/phase-12-slice-1-grounded-analysis-contracts.md`.

#### Forbidden Actions And Modules

- no NLP library or server orchestration;
- no semantic search or ontology matching logic in `core-types`;
- no provider-specific field or OpenAI name in a neutral contract;
- no browser, persistence, raw RDF, typed-operation payload, or source-write
  assumption in the contracts;
- no deletion of legacy Phase 11.5 contracts still used by tests or compatibility
  paths.

#### Expected Changes Or Output

Add the audited `phase-12-...-v1` contracts for:

- candidate extraction category and origin;
- stable candidate identity, normalized/display text, evidence spans, nearby
  participant/value hints, and resource version;
- retrieval query context and authorized scope;
- retrieval selection ID, canonical IRI, kind, source, labels, bounded
  definition, structural context, score, match reasons, and fingerprints;
- grounded dispositions `ReuseExisting`, `ExtendExisting`, `ProposeNew`, and
  `Unresolved`;
- grounded semantic items, connected references, prerequisites, rationale,
  confidence, and model supplements;
- complete candidate disposition ledger;
- distinct evidence, candidate, grounded-item, recommendation, and expanded-edit
  counts;
- `NeedsInput` recommendation status and editable field descriptions;
- Phase 12 work-key version inputs without credentials or raw documents;
- new deterministic analysis stages such as candidate extraction, retrieval,
  grounded modeling, grounded verification, compilation, and awaiting review.

All server-generated collections must require stable ordering and unique IDs.
Reuse and extension must require a selection ID rather than a model-provided
IRI.

#### Completion Artifact

`docs/decisions/phase-12-slice-1-grounded-analysis-contracts.md`

#### Tests

- Accept every valid candidate category, scope, decision, structural context,
  count, and `NeedsInput` field.
- Reject duplicate or unsorted IDs and reasons.
- Reject missing or cross-candidate selection references.
- Reject final IRIs used as model-owned selections.
- Reject invalid evidence, fingerprint, count, and confidence shapes.
- Prove stable ordering and equality for equivalent frozen inputs.
- Preserve legacy contract fixtures unless the audit explicitly versions them.

#### Verification Commands

```bash
./gradlew :core-types:test
./gradlew :core-types:build
git diff --check
```

#### Stop Conditions

- Stop if a neutral contract requires an RDF library or web-server type in
  `core-types`.
- Stop if additive compatibility is impossible without breaking current Phase
  11.5+ public review responses.
- Stop if `NeedsInput` cannot be distinguished from unsafe `Blocked` and
  unsupported `ReviewOnly` states.
- Stop if any contract introduces a task-wide valid-item, recommendation, or
  edit ceiling contrary to the spec.

### Slice 2: Implement Deterministic Local Candidate Extraction

#### Branch

`feature/phase-12-candidate-extraction`

#### Goal

Use the one audited local NLP library to create a stable evidence-linked
candidate inventory from existing extracted English text, without calling a
model or deciding ontology meaning.

#### Allowed Files And Modules

- `web-server/build.gradle.kts` for only the audited NLP and English resource
  dependencies;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentCandidateExtractionService.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionConfiguration.kt`
  for audited resource and contract versions;
- audited NLP resource paths only when Slice 0 proves dependency artifacts are
  insufficient;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentCandidateExtractionServiceTest.kt`;
- focused deterministic text fixtures under
  `web-server/src/test/resources/document-ingestion/`;
- `docs/decisions/phase-12-slice-2-deterministic-candidate-extraction.md`.

#### Forbidden Actions And Modules

- no provider call, ontology search, semantic reuse decision, or model prompt;
- no custom RDF, OWL, SHACL, parser, or general-purpose NLP framework;
- no second NLP library, native service, Python process, hosted API, embedding
  model, or vector dependency;
- no duplicate copies of English NLP model resources;
- no changes to upload, extraction, OCR, or temporary storage behavior;
- no browser-side candidate extraction.

#### Expected Changes Or Output

- Load the pinned English NLP resources once through a narrow server adapter.
- Extract named organizations, people, locations, dates, identifiers, and
  monetary values.
- Extract bounded concept terms, noun phrases, relationship phrases, nearby
  participants, attribute/value pairs, and obligation/condition/threshold cues
  using the audited NLP output and small Entio-specific rules.
- Mark likely administrative and illustrative candidates without treating that
  heuristic as final ontology meaning.
- Preserve exact block IDs, document IDs, pages or sections, offsets, and text.
- Derive stable IDs from document checksum, evidence location, normalized text,
  category, and contract/resource versions.
- Join exact duplicate spans only; do not merge similar business terms by token
  overlap.
- Produce stable ordering for frozen inputs.
- Fail with `document-candidate-extraction-failed` or the audited safe code when
  resources cannot load or processing cannot complete.

#### Completion Artifact

`docs/decisions/phase-12-slice-2-deterministic-candidate-extraction.md`

#### Tests

- Cover every required candidate category from fixed English text.
- Cover relationship participants, values, rules, and administrative text.
- Verify exact document/page/block/offset evidence.
- Verify deterministic IDs and order across repeated runs.
- Verify exact duplicate joining and similar-term separation.
- Verify malformed, cross-document, missing, and altered spans are rejected.
- Verify resource initialization failure is safe and contains no path or secret.
- Verify no provider is called.

#### Verification Commands

```bash
./gradlew :web-server:test --tests '*DocumentCandidateExtractionServiceTest*'
./gradlew :web-server:compileKotlin
./gradlew :web-server:dependencies
git diff --check
```

#### Stop Conditions

- Stop if the audited dependency or resource version differs from Slice 0.
- Stop if adding the dependency requires a new module, native service, Python
  runtime, external download at task time, or incompatible license.
- Stop if deterministic fixture output cannot be reproduced on the supported
  JVM.
- Stop if useful candidate extraction requires semantic choices that belong to
  the model rather than local NLP.
- Stop before modifying extraction or OCR to compensate for an NLP issue.

### Slice 3: Implement Deterministic Ontology Retrieval

#### Branch

`feature/phase-12-ontology-retrieval`

#### Goal

Create one focused semantic-engine retrieval service that coordinates existing
Entio search and matching behavior and returns stable Phase 12 selection records
without creating another index.

#### Allowed Files And Modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyRetrievalService.kt`;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyMatcher.kt`;
- `semantic-engine/src/main/kotlin/com/entio/semantic/SemanticDescriptionService.kt`
  only for an audited missing reusable query option;
- `semantic-engine/src/main/kotlin/com/entio/semantic/FiboSchemaSearchService.kt`
  only for an audited missing bounded adapter;
- `semantic-engine/src/test/kotlin/com/entio/semantic/DocumentOntologyRetrievalServiceTest.kt`;
- existing matcher and search tests when their reusable API changes;
- `docs/decisions/phase-12-slice-3-deterministic-ontology-retrieval.md`.

#### Forbidden Actions And Modules

- no web-server store dependency in `semantic-engine`;
- no ontology copy, index, cache with independent meaning, database, search
  server, embedding, vector, fuzzy model call, or external network request;
- no change to FIBO catalog content or generated files;
- no write to imported or FIBO entities;
- no model-owned selection or semantic reuse decision;
- no browser matching logic.

#### Expected Changes Or Output

- Accept candidates, a loaded project, explicit current-work/provenance records,
  same-task candidates, and the pinned FIBO session or approved FIBO records.
- Query applied local, imports, private draft, shared staging, proposal,
  same-task, durable provenance, and pinned FIBO in the audited order.
- Use candidate text, normalized forms, nearby relationship participants,
  attribute/value context, and compatible kind hints.
- Search all compatible kinds when a heuristic kind is uncertain.
- Normalize existing service results into one Phase 12 retrieval contract.
- Remove exact duplicate scope/IRI/source results without merging semantically
  similar entities.
- Include bounded labels, definitions, hierarchy, domains, ranges, datatypes,
  asserted types, match reasons, and fingerprints.
- Return at most 20 prompt-visible results per candidate using deterministic
  scoring, scope order, kind, canonical IRI, and source tie-breaking.
- Derive stable opaque selection IDs from the frozen candidate, entity, scope,
  source, ranking version, and fingerprints.
- Return an empty result as a successful search outcome.
- Expose a separate full-state exact duplicate/no-op check not limited by the
  top 20 prompt results.

#### Completion Artifact

`docs/decisions/phase-12-slice-3-deterministic-ontology-retrieval.md`

#### Tests

- Cover all authorized scopes and approved ordering.
- Cover local/imported descriptor search, same-task and provenance matching, and
  pinned FIBO results.
- Cover uncertain and explicit kind hints.
- Verify stable IDs, scores, reasons, ties, and order for frozen inputs.
- Verify the top-20 prompt bound and full-state duplicate checks independently.
- Verify empty results.
- Reject stale, cross-project, unapproved FIBO, wrong-source, and malformed
  records.
- Prove imported and FIBO results are read-only.
- Preserve existing semantic search and FIBO regression tests.

#### Verification Commands

```bash
./gradlew :semantic-engine:test --tests '*DocumentOntologyRetrievalServiceTest*' --tests '*DocumentOntologyMatcherTest*' --tests '*SemanticDescriptionServiceTest*' --tests '*FiboSchemaSearchServiceTest*'
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:build
git diff --check
```

#### Stop Conditions

- Stop if any authorized scope cannot be represented by explicit records without
  reversing module dependencies.
- Stop if ranking requires embeddings, external retrieval, or a second index.
- Stop if current search APIs must be rewritten broadly instead of adapted
  narrowly.
- Stop if top-20 ranking can hide an exact duplicate from the full-state safety
  check.
- Stop if a retrieval result could imply write authority over an import or FIBO.

### Slice 4: Add The Grounded Provider Boundary

#### Branch

`feature/phase-12-grounded-provider`

#### Goal

Add bounded provider requests that present evidence and retrieved choices
together and return only provider-neutral grounded semantic decisions.

#### Allowed Files And Modules

- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentGroundedAnalysisService.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
  only for the shared provider interface, retry budget, or failure classifier;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionConfiguration.kt`
  only for audited Phase 12 versions and provider bounds;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentGroundedAnalysisServiceTest.kt`;
- `web-server/src/test/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClientTest.kt`;
- `docs/decisions/phase-12-slice-4-grounded-provider-boundary.md`.

#### Forbidden Actions And Modules

- no orchestration switch to the new path yet;
- no model retrieval tool, function calling, filesystem access, network target,
  approval authority, or apply authority;
- no final IRI, raw RDF, Turtle, SPARQL, Entio operation kind, dependency order,
  or source-write field in the response schema;
- no full ontology dump or arbitrary search results;
- no silent model fallback;
- no unbounded correction or retry loop.

#### Expected Changes Or Output

- Build compact grounded request groups from candidates, exact evidence, nearby
  candidates, and their top-20 retrieval choices.
- Preserve candidate and evidence connections when forming groups.
- Treat document and retrieved text as untrusted data in system instructions.
- Require one grounded disposition for every modeled ontology item.
- Require a supplied selection ID for reuse and extension.
- Require connected domain/range, datatype range, individual type, assertion,
  and supported constraint roles where applicable.
- Accept explicitly marked model supplements only with candidate category and
  exact evidence references.
- Parse strict structured JSON into neutral Phase 12 contracts.
- Reject invented IDs, final IRIs used as selections, unknown fields, duplicate
  IDs, bad reference roles, and partial structured output.
- Use bounded correction for structurally invalid responses and adaptive
  splitting for output-limit or retryable oversized groups.
- Keep successful groups when another group splits or fails.
- Record logical calls separately from provider attempts.
- Use the user's verified selected compatible model for every grounded call.

#### Completion Artifact

`docs/decisions/phase-12-slice-4-grounded-provider-boundary.md`

#### Tests

- Verify prompt content contains compact evidence and returned choices, not a
  full ontology dump.
- Verify prompt-injection instructions remain ordinary document data.
- Parse all four dispositions and every supported connected role.
- Reject final IRIs, operation DTOs, unknown selection IDs, duplicate IDs,
  missing evidence, and incompatible roles.
- Cover model supplements.
- Cover exact-input retry, adaptive split, correction exhaustion, output-token
  failure, HTTP 500, authorization, quota, rate limit, timeout, and cancellation.
- Prove no silent model switch and no provider call after cancellation.
- Verify logs and safe errors contain no credential, full prompt, full document,
  or raw provider response.

#### Verification Commands

```bash
./gradlew :web-server:test --tests '*DocumentGroundedAnalysisServiceTest*' --tests '*OpenAiDocumentAnalysisClientTest*'
./gradlew :web-server:compileKotlin
git diff --check
```

#### Stop Conditions

- Stop if the selected provider cannot enforce the audited structured response
  without accepting partial JSON.
- Stop if a useful response requires low-level Entio operations or final IRIs.
- Stop if grouping cannot retain every candidate disposition after adaptive
  splitting.
- Stop if the provider budget would silently omit candidates or exceed approved
  retries.
- Stop if implementing the contract requires a provider-specific type in
  `core-types` or `semantic-engine`.

### Slice 5: Wire Production Orchestration And The Frozen Work Key

#### Branch

`feature/phase-12-grounded-orchestration`

#### Goal

Make new document tasks run candidate extraction and ontology retrieval before
grounded modeling, gather every authorized current-work scope, freeze all inputs,
and report clear stage-specific counts.

#### Allowed Files And Modules

- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentRetrievalContextFactory.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestrator.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionWebService.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionTaskManager.kt`
  only for additive count and stage records;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionConfiguration.kt`;
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt` only for
  the audited additive read-only current-work snapshot;
- `web-server/src/main/kotlin/com/entio/web/Application.kt` only if constructor
  wiring is required;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestratorTest.kt`;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentTaskLifecycleTest.kt`;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentIngestionBoundsTest.kt`;
- `web-server/src/test/kotlin/com/entio/web/DocumentIngestionRouteIntegrationTest.kt`
  only for additive progress response coverage;
- `docs/decisions/phase-12-slice-5-orchestration-and-work-key.md`.

#### Forbidden Actions And Modules

- no change to intake, extraction, OCR, temporary file cleanup, credentials, or
  model selection;
- no semantic policy in `StagingWorkflowService` or route handlers;
- no mutation of staging, proposal, review, provenance, imports, or FIBO while
  building retrieval context;
- no ontology-blind fallback when retrieval fails;
- no deletion of legacy services still required by compatibility tests;
- no truncation followed by a successful completion status.

#### Expected Changes Or Output

- Replace the active new-task sequence after extraction with candidate
  extraction, retrieval, grounded modeling, grounded verification handoff, and
  existing compilation.
- Build read-only records for applied, imported, private-draft, shared-staging,
  current-proposal, same-task, durable-provenance, and pinned-FIBO scopes.
- Load the pinned FIBO session through the existing loader; do not copy its
  catalog.
- Freeze document, extraction, evidence, candidate, retrieval, ontology,
  current-work, provenance, FIBO, selected-model, prompt, response, ranking, and
  NLP resource versions in the Phase 12 work key.
- Invalidate or refresh retrieval before a provider call when an ontology or
  current-work fingerprint changes.
- Block compilation when a model result refers to stale retrieval state.
- Record deterministic candidate extraction and retrieval stages with zero
  provider attempts.
- Record grounded calls with logical-call and provider-attempt counts.
- Report evidence-block, NLP-candidate, grounded-item, recommendation, and
  expanded-edit counts as separate named values.
- Fail explicitly when an emergency resource safeguard prevents complete work;
  never report complete coverage after truncation.
- Preserve cancellation, cleanup, task isolation, and no-write behavior.

#### Completion Artifact

`docs/decisions/phase-12-slice-5-orchestration-and-work-key.md`

#### Tests

- Prove retrieval completes before the first grounded semantic call.
- Prove every applicable scope is represented in the context factory.
- Prove same-task candidates include other documents.
- Prove frozen input equivalence produces the same work key.
- Prove changes to candidates, retrieval ranking, ontology, current work,
  provenance, FIBO, NLP resources, prompt, or model change the work key.
- Prove stale retrieval is refreshed before modeling and stale model output is
  blocked before compilation.
- Prove empty retrieval results continue.
- Prove retrieval failure does not fall back to ontology-blind modeling.
- Prove count units and stage records are correct.
- Prove cancellation and emergency failure leave sources unchanged and do not
  claim complete coverage.

#### Verification Commands

```bash
./gradlew :web-server:test --tests '*DocumentIngestionOrchestratorTest*' --tests '*DocumentTaskLifecycleTest*' --tests '*DocumentIngestionBoundsTest*' --tests '*DocumentIngestionRouteIntegrationTest*'
./gradlew :web-server:build
git diff --check
```

#### Stop Conditions

- Stop if any required scope cannot be read without mutating workflow state.
- Stop if current-work retrieval would expose another project or user.
- Stop if the work key cannot represent every stale-sensitive input.
- Stop if the new path would require changing extraction or creating durable task
  state.
- Stop if a provider budget cannot finish the bounded benchmark without silently
  omitting candidates; amend the audited resource safeguard instead of deleting
  work.

### Slice 6: Verify Grounded Decisions And Integrate Compilation

#### Branch

`feature/phase-12-grounded-verification`

#### Goal

Deterministically verify grounded selections and supplements, keep connected
prerequisites with their main items, convert reviewer-solvable gaps into
`NeedsInput`, and pass supported meaning to the existing semantic compiler.

#### Allowed Files And Modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentGroundedAnalysisVerifier.kt`;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyRetrievalService.kt`
  only for supplemental retrieval and full-state revalidation;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyMatcher.kt`
  only for exact existing-target validation;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentSemanticPlanCompiler.kt`;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentChangeSetPlanVerifier.kt`;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentCompletenessMetricService.kt`;
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentRecommendationDraftTranslator.kt`
  only for the verified Phase 12 result;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentGroundedAnalysisService.kt`
  only for service coordination around the verifier;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestrator.kt`
  only to install the verified result into review;
- matching focused tests under `semantic-engine/src/test/kotlin/com/entio/semantic/`;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentAnalysisServiceTest.kt`;
- `docs/decisions/phase-12-slice-6-verification-and-compilation.md`.

#### Forbidden Actions And Modules

- no Kotlin guess that similar labels have the same business meaning;
- no automatic selection of a different ontology candidate merely to make a
  model choice compile;
- no strict direct-evidence requirement for the exact label of a clearly marked,
  connected, editable model-recommended prerequisite;
- no separate unattached prerequisite recommendation;
- no forcing unsupported complex rules into an incorrect SHACL or ontology
  pattern;
- no second compiler, raw RDF, or new typed operation family;
- no task-wide semantic item, recommendation, or edit ceiling.

#### Expected Changes Or Output

- Verify response versions, IDs, evidence, candidate references, connected
  references, and dispositions.
- Resolve reuse and extension only through selection IDs from the frozen request
  or a reviewer-authorized refreshed retrieval result.
- Revalidate IRI, kind, scope, source, fingerprints, and read-only status.
- Run full-state duplicate, collision, no-op, same-task, current-work, and prior
  provenance checks independent of top-20 prompt visibility.
- Verify object-property domains/ranges, datatype-property domains/datatypes,
  individual types, assertion roles, and supported constraint targets.
- Turn missing reviewer-solvable match, source, domain, range, datatype, type,
  or prerequisite values into typed `NeedsInput` fields with compatible choices.
- Keep model-recommended prerequisites in the connected component they support
  and mark them explicitly.
- Verify model supplements against exact evidence, run retrieval, and change a
  create decision to unresolved when a plausible existing match appears.
- Produce exactly one coverage disposition for every candidate and supplement.
- Map verified items to the existing `DocumentSemanticPlan` and compiler.
- Keep unsupported complex meaning as related review-only context.
- Split an oversized atomic recommendation only when every result remains
  coherent and dependency-safe; otherwise block that recommendation without
  affecting independent groups.
- Remove any task-wide edit/recommendation ceiling behavior identified for
  retirement in Slice 0 while preserving the 20-edit internal staging batch.

#### Completion Artifact

`docs/decisions/phase-12-slice-6-verification-and-compilation.md`

#### Tests

- Cover valid reuse, extension, new, and unresolved items.
- Reject invented, stale, wrong-kind, wrong-source, cross-project, imported-write,
  and FIBO-write selections.
- Cover full-state duplicate and no-op detection outside the top 20.
- Cover every property, datatype, individual, assertion, and constraint role.
- Cover connected explicit and model-recommended prerequisites.
- Cover `NeedsInput` instead of avoidable blocked/review-only results.
- Cover model supplements with and without plausible existing matches.
- Cover exact complete dispositions and independent-group continuation.
- Compile all existing supported semantic patterns through current typed
  translators.
- Keep separation-of-duty, aggregation, procedural, temporal, and unsupported
  conditional rules non-executable.
- Cover atomic splitting and bounded internal batches without a task-wide edit
  ceiling.
- Preserve validation, semantic diff, reasoning, SHACL, and rollback regressions.

#### Verification Commands

```bash
./gradlew :semantic-engine:test --tests '*DocumentGroundedAnalysisVerifierTest*' --tests '*DocumentSemanticPlanCompilerTest*' --tests '*DocumentChangeSetPlanVerifierTest*' --tests '*DocumentCoverageMetricServiceTest*' --tests '*DocumentRecommendationDraftTranslatorTest*'
./gradlew :web-server:test --tests '*DocumentAnalysisServiceTest*' --tests '*DocumentIngestionOrchestratorTest*'
./gradlew :semantic-engine:build
./gradlew :web-server:build
git diff --check
```

#### Stop Conditions

- Stop if a model choice can compile only by changing its semantic target.
- Stop if required verification needs semantic policy in React or Ktor routes.
- Stop if a model supplement can bypass full retrieval and duplicate checks.
- Stop if `NeedsInput` would allow acceptance before required fields pass Kotlin
  verification.
- Stop if existing typed services cannot express a claimed executable pattern;
  retain it as review-only or amend the spec rather than inventing RDF behavior.
- Stop if removing a task ceiling weakens an atomic batch or memory safety check
  instead of replacing silent truncation with explicit bounded processing.

### Slice 7: Add Connected Editable Review And Bounded Draft Batching

#### Branch

`feature/phase-12-connected-review`

#### Goal

Expose grounded choices, alternatives, prerequisites, `NeedsInput`, and distinct
counts in compact collapsible review, and reverify every reviewer edit before
acceptance and bounded draft staging.

#### Allowed Files And Modules

- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentReviewWorkspace.kt`;
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionWebService.kt`;
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt` only for
  existing atomic document-draft batching;
- `web-server/src/main/kotlin/com/entio/web/Application.kt` only for additive or
  versioned request/response serialization;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentReviewWorkspaceTest.kt`;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentDraftProposalIntegrationTest.kt`;
- `web-server/src/test/kotlin/com/entio/web/DocumentIngestionRouteIntegrationTest.kt`;
- `web-app/src/web/projectApi.ts`;
- `web-app/src/web/projectApi.test.ts`;
- `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.tsx`;
- `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.test.tsx`;
- `web-app/src/styles.css`;
- `web-app/e2e/document-ingestion.spec.ts`;
- `docs/decisions/phase-12-slice-7-connected-editable-review.md`.

#### Forbidden Actions And Modules

- no browser-owned semantic matching, kind compatibility, duplicate decision,
  IRI generation, or typed-operation construction;
- no direct use of model-provided IRIs as existing-entity selections;
- no acceptance of `NeedsInput`, `Blocked`, or unconfirmed individual work;
- no automatic apply or new proposal path;
- no durable review-state store;
- no task-wide maximum-accepted-edit UI or server behavior.

#### Expected Changes Or Output

- Add public review fields for grounded disposition, selected selection ID,
  alternative retrieval candidates, match reasons, structural context,
  prerequisite origin, editable semantic fields, and distinct count totals.
- Keep canonical IRIs visible for review while using server-issued selection IDs
  for decisions.
- Keep every recommendation collapsed by default.
- Show only name, edit type, confidence, status, and expansion control in the
  collapsed summary.
- Show intent, exact changes, evidence, alternatives, generated IRIs, sources,
  domains, ranges, datatypes, types, prerequisites, confidence, review-only
  context, and safe warnings when expanded.
- Expose edits for disposition, match, supported kind, label, definition,
  writable source, domain, range, datatype, type, and model-recommended
  prerequisite fields.
- Submit only server-issued IDs and explicit field values.
- Return every edited recommendation to pending review and run server-side
  verification and recompilation.
- Use `NeedsInput` when ordinary reviewer input can complete the item;
  `ReviewOnly` only for unsupported meaning; `Blocked` only for unsafe work.
- Remove `maximumAcceptedEdits` or version it as a deprecated compatibility
  field with no enforcement, according to Slice 0.
- Divide accepted executable changes into dependency-safe batches of at most 20
  typed edits while keeping one existing proposal-review package.
- Preserve individual confirmation, stale-state, validation, apply, reload,
  rollback, and provenance behavior.

#### Completion Artifact

`docs/decisions/phase-12-slice-7-connected-editable-review.md`

#### Tests

- Verify collapsed summaries contain exactly the required visible fields.
- Verify expand/collapse accessibility and keyboard behavior.
- Verify alternatives and match reasons render without allowing arbitrary IRIs.
- Verify each supported field edit round-trips through the server and triggers
  revalidation.
- Verify invalid kind/domain/range/datatype/type combinations remain
  non-acceptable.
- Verify explicit and model-recommended prerequisites remain visually distinct.
- Verify `NeedsInput`, `Executable`, `Mixed`, `ReviewOnly`, and `Blocked` behavior.
- Verify distinct candidate, item, recommendation, and edit counts.
- Verify hundreds of valid edits are staged in bounded batches without a task
  ceiling or data loss.
- Verify no browser action approves or applies changes.
- Preserve proposal, source-unchanged-before-approval, apply, reload, rollback,
  and provenance integration tests.

#### Verification Commands

```bash
./gradlew :web-server:test --tests '*DocumentReviewWorkspaceTest*' --tests '*DocumentDraftProposalIntegrationTest*' --tests '*DocumentIngestionRouteIntegrationTest*'
npm --prefix web-app test -- --run projectApi DocumentIngestionWorkspace
npm --prefix web-app run build
npm --prefix web-app run test:e2e -- --grep "document ingestion"
git diff --check
```

#### Stop Conditions

- Stop if reviewer edits can bypass Kotlin verification or construct operations
  in TypeScript.
- Stop if a canonical IRI must be accepted from arbitrary browser text instead
  of a server-issued selection.
- Stop if batching can separate a prerequisite from its dependent edit or leave
  a misleading partial proposal.
- Stop if compatibility requires a second review or apply workflow.
- Stop if the UI cannot distinguish model-recommended prerequisites from
  document-explicit meaning.

### Slice 8: Permanent Regression, Security, And Controlled Benchmark

#### Branch

`test/phase-12-regression-benchmark`

#### Goal

Prove the complete Phase 12 pipeline is deterministic where Kotlin owns
behavior, safe across trust boundaries, and measurably better grounded across
the permanent two-document provider benchmark.

#### Allowed Files And Modules

- existing Phase 12 and document-ingestion tests under `core-types`,
  `semantic-engine`, `web-server`, and `web-app`;
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentSemanticProviderBenchmarkTest.kt`;
- focused Phase 12 integration/security tests under
  `web-server/src/test/kotlin/com/entio/web/ingestion/`;
- `web-app/e2e/document-ingestion.spec.ts`;
- the existing Phase 11.5 expectation manifest, read-only for historical
  expectations;
- one Phase 12-only expectation supplement at
  `web-server/src/test/resources/document-ingestion/phase-12-two-pdf-expectations.json`;
- the two existing PDFs at their current paths, read-only;
- Phase 12 production files from Slices 1–7 only for a narrowly demonstrated
  defect in already approved Phase 12 behavior;
- `docs/decisions/phase-12-slice-8-benchmark-and-regression.md`.

#### Forbidden Actions And Modules

- no duplicate PDF, copied ontology project, timestamped fixture, alternate
  benchmark test, or rewritten historical Phase 11.5 manifest;
- no weakening expectations to make a provider run pass;
- no production behavior expansion, prompt-only special case for fixture names,
  hidden model switch, hard-coded provider secret, or automatic apply;
- no committed raw prompts, responses, documents, credentials, environment
  files, logs, or benchmark scratch output;
- no benchmark network call in the ordinary test suite.

#### Expected Changes Or Output

- Extend the existing benchmark harness rather than creating a competing
  benchmark path.
- Keep historical positive and negative expectations and add only Phase 12
  candidate/retrieval/reuse expectations in the Phase 12 supplement.
- Freeze document checksums, ontology and current-work fingerprints, candidate
  inventory, retrieval results, prompt/response versions, selected model, and
  scoring inputs.
- Add offline recorded fixtures for deterministic candidate extraction,
  retrieval ranking, selection validation, supplements, prerequisites, counts,
  compilation, review editing, and no-write behavior.
- Add security coverage for prompt injection, cross-project IDs, stale IDs,
  unauthorized sources, secret/path redaction, cancellation, and temporary
  cleanup.
- Add scale coverage proving chunking and 20-edit draft batching do not silently
  truncate valid meaning.
- Run ten controlled trials with an explicitly supplied verified credential and
  exact model ID.
- Record concepts, relationships, expected reuse, duplicate-new recommendations,
  unresolved choices, compilation success, prohibited outputs, evidence,
  provenance, attempts, duration, tokens when available, and failures.
- Require the spec thresholds: concepts at least 9/10, relationships at least
  8/10, expected unambiguous reuse at least 9/10, duplicate new entities 0/10,
  prohibited executable recommendations 0/10, provenance 10/10, supported
  compilation at least 95%, unsupported rules non-executable 10/10, and
  automatic writes 0/10.

#### Completion Artifact

`docs/decisions/phase-12-slice-8-benchmark-and-regression.md`

The artifact records frozen hashes, model ID, commands, aggregate results,
redacted failure categories, durations, available usage data, and whether any
retry or correction was needed. It must not contain the credential or raw
provider payloads.

#### Tests

- Run all offline candidate, retrieval, provider-contract, verifier, compiler,
  review, security, scale, lifecycle, route, and end-to-end tests.
- Run the complete existing Kotlin and TypeScript regression suites.
- Run the controlled benchmark only when explicitly enabled.
- Confirm Git contains no secret, generated model cache, raw provider capture,
  copied PDF, or ignored build output.

#### Verification Commands

Offline:

```bash
./gradlew test
./gradlew build
./gradlew check
./gradlew :semantic-engine:verifyFiboCatalog
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

Controlled provider benchmark, using the exact environment names pinned by
Slice 0:

```bash
OPENAI_API_KEY="<local credential>" \
  ENTIO_DOCUMENT_BENCHMARK=true \
  ENTIO_DOCUMENT_BENCHMARK_MODEL="<exact verified model id>" \
  ./gradlew :web-server:test --tests '*DocumentSemanticProviderBenchmarkTest*'
```

#### Stop Conditions

- Stop if any deterministic, security, lifecycle, scale, build, or end-to-end
  test fails.
- Stop if the controlled provider benchmark misses any approved threshold.
- Stop if a duplicate expected reuse target appears as new in any trial.
- Stop if any ontology or SHACL source changes without explicit approval.
- Stop if a credential, raw provider payload, copied fixture, generated cache,
  or unrelated file appears in Git status.
- Do not record a benchmark waiver without explicit user approval; a missing
  credential blocks phase completion.

### Slice 9: Final Documentation And Phase Completion

#### Branch

`docs/phase-12-completion`

#### Goal

Align current repository documentation with the verified Phase 12 production
path and record phase completion without changing behavior.

#### Allowed Files And Modules

- `AGENTS.md`;
- `README.md`;
- `docs/architecture/document-analysis-and-adaptive-recovery.md`;
- `docs/architecture/ai-subsystem-map.md`;
- `docs/architecture/repository-structure-and-code-architecture.md`;
- `docs/architecture/phase-12-scope.md` status and final alignment only;
- `docs/specs/0023-phase-12-ontology-grounded-document-analysis.md` status and
  final alignment only;
- this ExecPlan status and final alignment only;
- `docs/phase-summaries/phase-12-summary.md`;
- `docs/decisions/phase-12-slice-9-phase-completion.md`.

#### Forbidden Actions And Modules

- no production, test, dependency, fixture, prompt, or benchmark-code change;
- no claim that Phase 12 is complete before Slice 8 passes;
- no claim that the model or whole pipeline is deterministic;
- no embeddings, vector database, external retrieval, automatic approval, or
  other future-phase promise;
- no rewrite of historical Phase 11, 11.5, or 11.5+ delivery records.

#### Expected Changes Or Output

- Mark the Phase 12 scope, spec, and ExecPlan implemented only after all prior
  completion artifacts and verification evidence exist.
- Update `AGENTS.md` and `README.md` current repository status and capability
  lists.
- Rewrite the current document-analysis architecture description to show the
  Phase 12 order and distinguish deterministic retrieval from model judgment.
- Update subsystem and repository maps only where Phase 12 changes the current
  production path.
- Record the audited NLP dependency and the absence of embeddings/vector
  infrastructure.
- Record all slice branches, commits, completion artifacts, benchmark outcome,
  verification commands, and no-write guarantees in the phase summary.
- Preserve explicit non-goals and the existing only-write-path description.

#### Completion Artifacts

- `docs/phase-summaries/phase-12-summary.md`
- `docs/decisions/phase-12-slice-9-phase-completion.md`

#### Tests

- Review documentation links, dates, phase status, file names, and contract
  names against implemented source and completion artifacts.
- Confirm the described production flow matches the orchestrator.
- Confirm all non-goals remain explicit.
- Rerun full verification because documentation is the final slice boundary.

#### Verification Commands

```bash
./gradlew test
./gradlew build
./gradlew check
./gradlew :semantic-engine:verifyFiboCatalog
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

#### Stop Conditions

- Stop if any earlier completion artifact or pushed slice branch is missing.
- Stop if the controlled benchmark is missing or below threshold without an
  explicit recorded waiver.
- Stop if documentation would need to describe behavior not present in source.
- Stop if full verification fails or the working tree contains unrelated or
  generated files.
- Do not declare Phase 12 complete or merge this slice until all conditions pass.

## Step-By-Step Implementation Plan

1. Freeze current behavior and resolve the NLP dependency, exact contracts,
   current-work adapters, compact context, safeguards, and benchmark manifest.
2. Add neutral Phase 12 candidate, retrieval, grounded-decision, count,
   `NeedsInput`, and work-key contracts.
3. Add deterministic local candidate extraction over existing located text.
4. Add deterministic retrieval over existing local/imported search, current
   work, provenance, same-task candidates, and pinned FIBO.
5. Add bounded grounded provider requests and strict structured response
   parsing.
6. Switch new tasks to extraction → candidate extraction → retrieval → grounded
   modeling while preserving lifecycle and safe failure behavior.
7. Verify selections, supplements, prerequisites, coverage, duplicates, kinds,
   sources, and freshness before existing deterministic compilation.
8. Expose compact editable connected review and stage accepted work in bounded
   dependency-safe batches.
9. Pass offline deterministic, security, scale, full regression, and ten-run
   controlled provider gates.
10. Align current documentation and record verified Phase 12 completion.

## Test Plan

### Contract tests

- Neutral DTO construction and invalid-shape rejection.
- Stable IDs, versions, ordering, counts, selection references, and work keys.
- Compatibility with retained Phase 11.5+ contracts.

### Candidate extraction tests

- Named entities, concepts, relationships, values, rules, administrative text,
  exact spans, deterministic IDs, duplicate handling, and resource failure.

### Retrieval tests

- Every authorized scope, stable ranking, top-20 prompt results, full-state
  duplicate checks, kinds, structural context, empty results, stale state,
  imported/FIBO read-only behavior, and project isolation.

### Grounded provider tests

- Four dispositions, supplied selection IDs, connected roles, supplements,
  prompt injection, strict JSON, adaptive splitting, retry classification,
  cancellation, redaction, and no model fallback.

### Verification and compilation tests

- Evidence, selections, fingerprints, kinds, domains, ranges, datatypes, types,
  assertions, prerequisites, supplements, complete coverage, duplicates,
  no-ops, supported compilation, unsupported rules, atomic splitting, and no
  task-wide semantic ceiling.

### Review tests

- Collapsed summary, alternatives, match reasons, editable semantic fields,
  `NeedsInput`, server revalidation, distinct counts, individual gates, bounded
  draft batches, no browser-owned policy, and no write before approval.

### Security and lifecycle tests

- Prompt injection, cross-project and cross-user isolation, stale IDs,
  unauthorized scopes, secret/path redaction, cancellation, cleanup, provider
  failure, and atomic rollback.

### Permanent fixture and provider tests

- Offline frozen two-document candidate/retrieval expectations.
- Ten controlled identical trials using an explicitly configured credential and
  model.
- Positive, negative, reuse, duplicate, compilation, provenance, unsupported
  rule, and no-write thresholds from the spec.

### Full regression

- All Kotlin modules.
- React unit, build, audit, and end-to-end suites.
- VS Code regression suite, even though Phase 12 does not change it.
- Pinned FIBO verification.
- Git whitespace and clean-tree checks.

## Full Verification Commands

Run after every slice-specific command has passed and again after Slice 9:

```bash
./gradlew test
./gradlew build
./gradlew check
./gradlew :semantic-engine:verifyFiboCatalog
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

Run the controlled benchmark before phase completion:

```bash
OPENAI_API_KEY="<local credential>" \
  ENTIO_DOCUMENT_BENCHMARK=true \
  ENTIO_DOCUMENT_BENCHMARK_MODEL="<exact verified model id>" \
  ./gradlew :web-server:test --tests '*DocumentSemanticProviderBenchmarkTest*'
```

The implementation must use the exact environment names, model ID, and command
recorded by Slice 0. The example above does not authorize committing a key or
printing it in logs.

## Rollback Notes

Rollback follows slice boundaries in reverse dependency order.

- Slice 9 is documentation only and can be reverted without runtime effect.
- Slice 8 removes Phase 12 benchmark and regression additions but must not
  delete the permanent PDFs or historical Phase 11.5 expectations.
- Slice 7 reverts Phase 12 review fields and UI while preserving existing
  connected review and proposal behavior.
- Slice 6 reverts grounded verification adapters and returns compilation to the
  prior verified semantic-plan input.
- Slice 5 restores the prior active production sequence. Retained legacy
  discovery and connected-model services must remain available until Phase 12
  is verified specifically to make this rollback possible.
- Slice 4 removes the unused grounded provider path after Slice 5 is reverted.
- Slice 3 removes the retrieval facade while leaving the existing semantic
  search, matcher, and FIBO services unchanged.
- Slice 2 removes the local candidate service and its one audited dependency and
  resources.
- Slice 1 removes unused neutral Phase 12 contracts after all consumers are
  reverted.
- Slice 0 retains a historical audit record unless the entire planning effort is
  intentionally abandoned.

Rollback must not:

- delete user ontology changes;
- reset or rewrite repository history;
- delete applied-document provenance;
- alter the pinned FIBO package;
- remove shared search behavior used by other product surfaces;
- use a force push;
- leave half of a versioned public contract active.

If a rollback touches already applied user ontology content, stop. Source
recovery must use Entio's existing proposal rollback behavior or an explicitly
approved repository recovery procedure, not ad hoc file deletion.

## Risks And Assumptions

### Risks

- A JVM NLP library may add substantial artifacts or startup cost.
- Traditional NER and part-of-speech tools may miss domain-specific business
  concepts or relationships.
- Lexical retrieval may miss synonyms because Phase 12 intentionally excludes
  embeddings.
- Combining results from several current-work scopes may create ambiguous ties.
- Prompt context may grow when many candidates each have 20 choices.
- Model supplements can reintroduce duplicate risk if post-model retrieval is
  incomplete.
- Reviewer editing across shared prerequisites can complicate dependency-safe
  batching.
- Removing task-wide semantic ceilings can increase memory and runtime even
  while request and batch bounds remain.
- Provider behavior may remain variable even with grounded context.
- Existing dirty or divergent repository state at implementation time can make
  slice ownership unclear.

### Mitigations

- Slice 0 can reject an unsuitable NLP dependency before production changes.
- Candidate extraction is a seed inventory, not the semantic authority; verified
  model supplements preserve recall.
- Retrieval exposes deterministic match reasons and unresolved choices rather
  than pretending lexical search proves identity.
- Stable scope order, tie-breaking, fingerprints, and server-issued IDs make
  retrieval repeatable.
- Candidate grouping and adaptive splitting bound provider payloads.
- Every supplement receives full retrieval and duplicate checks.
- Shared prerequisites remain explicit dependencies and are recompiled after
  edits.
- Emergency resource safeguards fail visibly and never claim complete coverage
  after truncation.
- Ten controlled trials measure remaining model variability.
- Every slice starts from a clean accumulated base and has a separate completion
  artifact and stop conditions.

### Assumptions

- The Phase 12 scope and spec are approved before Slice 0 begins.
- A suitable established JVM-compatible English NLP library exists.
- Existing semantic descriptors, matcher records, staging state, provenance, and
  FIBO sessions can supply the required retrieval inputs without new
  persistence.
- Existing typed ontology and SHACL services remain sufficient for the
  executable patterns already approved in Phase 11.5+.
- The two permanent PDFs and simple ontology project remain available.
- A verified provider credential and exact compatible model can be supplied for
  the controlled completion benchmark.
- The implementation base is clean and current according to `AGENTS.md` before
  any slice branch is created.

## Definition Of Done

Phase 12 is done only when:

1. All ten slices are implemented in the listed dependency order.
2. Every slice has its exact completion artifact, focused commit, pushed branch
   when authorized, and clean local merge into the accumulated base.
3. Local NLP candidate extraction runs before semantic provider calls and is
   deterministic for frozen inputs.
4. Every retained candidate is searched across every applicable authorized
   scope before grounded modeling.
5. Retrieval reuses existing Entio services and adds no embeddings, vector
   database, second ontology index, or external retrieval service.
6. Grounded model output uses only reuse, extend, propose-new, unresolved, and
   explicit non-ontology dispositions.
7. Reuse and extension selections use server-issued IDs and pass Kotlin
   freshness, kind, source, and scope checks.
8. Model supplements pass evidence, retrieval, and full duplicate checks before
   new creation can compile.
9. Properties, individuals, assertions, and constraints carry their required
   connected context.
10. Model-recommended prerequisites are attached, visible, editable, and
    deterministically verified.
11. Reviewer-solvable gaps appear as `NeedsInput`; unsupported meaning remains
    visible; unsafe work remains blocked.
12. Valid supported meaning compiles through the existing Phase 11.5+ compiler
    and typed edit services only.
13. Completed recommendations are collapsed by default and expose only the
    required compact summary until expanded.
14. Every supported reviewer edit is reverified and recompiled in Kotlin.
15. Candidate, grounded-item, recommendation, and expanded-edit counts remain
    distinct and correct.
16. No valid item, recommendation, or edit is silently dropped or made
    review-only because of a task-wide product ceiling.
17. Accepted work is staged in dependency-safe batches and enters one existing
    proposal-review workflow.
18. No ontology or SHACL source changes before explicit human approval.
19. Existing apply, reload, rollback, and applied-provenance behavior remains
    green.
20. Offline deterministic, security, scale, lifecycle, unit, integration, build,
    audit, end-to-end, VS Code, and FIBO verification passes.
21. The ten-run controlled provider benchmark meets every spec threshold with no
    secret or raw payload committed.
22. `AGENTS.md`, `README.md`, current architecture documentation, planning
    statuses, completion decisions, and the Phase 12 summary match the verified
    implementation.
23. No unrelated file, generated artifact, credential, environment file, cache,
    or log is changed or committed.
24. The accumulated local base is clean. The base branch is not pushed unless
    explicitly authorized.

## Boundary Check

- Phase 12 is an explicitly proposed new phase with a scope and feature spec.
- The plan changes only the document-analysis order, grounded contracts,
  retrieval adapter, verification integration, and review behavior needed by
  that phase.
- `core-types` owns neutral data; `semantic-engine` owns retrieval and semantic
  verification; Ktor owns local NLP and provider orchestration; React owns
  presentation and explicit review input.
- Lower-level modules do not depend on web-server or web-app.
- Existing RDF, OWL, SHACL, semantic search, matcher, FIBO, compiler, proposal,
  apply, reload, rollback, and provenance services are reused.
- No product logic enters `shared`.
- No embeddings, vector database, second ontology index, external hosted
  retrieval, new persistence, new module, autonomous agent, raw RDF, automatic
  approval, second write path, CLI ingestion, or VS Code ingestion is added.
- The model interprets business meaning but does not determine deterministic
  validity or write ontology content.
- Kotlin remains the source of truth for evidence, selection identity,
  freshness, ontology structure, duplicates, compilation, and safety.
