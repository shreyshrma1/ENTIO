# ExecPlan: Phase 11 AI-Powered Document Ingestion And Ontology Evolution

## Status

Implemented and verified on 2026-07-24. All ten ordered slices are complete.
Slice 0 was approved in
[`phase-11-slice-0-contract-audit.md`](../decisions/phase-11-slice-0-contract-audit.md),
and the final acceptance evidence is recorded in
[`phase-11-slice-8-verification.md`](../decisions/phase-11-slice-8-verification.md).

## Goal

Add a bounded web workflow that turns supported business documents into evidence-linked recommendations and then into existing typed private-draft operations chosen by a human reviewer.

The implementation path is:

```text
safe upload
→ located text extraction
→ bounded AI candidate analysis
→ Kotlin evidence verification and ontology matching
→ document comparison and recommendations
→ human review
→ typed private-draft batches
→ existing validation and proposal workflow
```

No Phase 11 component approves, applies, or writes ontology content directly.

## Related Spec

- [Phase 11 scope](../architecture/phase-11-scope.md)
- [Phase 11 feature spec](../specs/0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md)

## Current State

- `semantic-engine` owns project loading, semantic descriptors, deterministic search, imports, FIBO matching, typed edit translation, validation inputs, reasoning, SHACL integration, and proposal application.
- `web-server` owns approved-project registration, development identity, shared staging, proposal orchestration, semantic jobs, in-memory credentials, and model discovery/selection.
- The current product exposes an active native ontology assistant backed by `OpenAiProposalClient` and the OpenAI Responses API.
- `AiProposalService` owns in-memory project conversations and runs, ontology-aware context, response routing, optional FIBO context, validation/repair, cancellation, rejection, edit removal, and staging handoff.
- `Application.kt` registers project-scoped AI proposal routes, and `ProjectWorkspace` renders `AiProposalPanel` as the Entio AI sidebar.
- Assistant output is review-only: it has no arbitrary tools, direct source-write path, approval authority, or automatic apply path.
- Assistant conversations, proposal runs, model settings, and history are in-memory, and the browser polls run status rather than using SSE.
- `web-app` owns the browser workbench, settings, shared staging, proposal review, reasoning, SHACL, FIBO, and Explore presentation.
- The repository has no approved upload contract, document extraction service, OCR adapter, ingestion task model, evidence record, or ingestion review workspace.
- The repository has no production document store, queue, durable ingestion provenance store, database, or production identity layer.

## Target State

- Authorized users can upload up to ten bounded PDF, DOCX, TXT, or Markdown files to one project-scoped ingestion task.
- Temporary server storage holds original documents and extraction artifacts outside ontology project directories.
- Kotlin produces deterministic located-text blocks and uses embedded PDF text before page-level OCR.
- A narrow document-analysis adapter reuses the active assistant's credential, verified-model, and OpenAI provider boundaries to analyze bounded text into candidate records.
- Kotlin verifies excerpts, assigns identities, searches local/imported/current-work/FIBO scopes, detects duplicates, and compares new evidence with both current ontology content and retained provenance from earlier completed ingestion workflows.
- The browser shows evidence-linked document summaries, separate schema and fact recommendations, conflicts, and draft impact.
- User-approved recommendations convert into supported typed private-draft batches and then use the existing proposal workflow.
- Tasks expose progress, cancellation, safe failure, bounded retry, exact-work reuse, and current-session history.
- Restart or task deletion removes temporary task execution state, but provenance for applied document-derived ontology changes remains durably available so later ingestion workflows can compare new evidence with earlier supporting evidence.

## Architecture And Ownership

### `core-types`

Own neutral immutable records for documents, located text, evidence, candidates, recommendations, comparison outcomes, review decisions, and draft provenance. These records must not contain Ktor, React, provider SDK, PDFBox, POI, Tesseract, or Jena types.

### `semantic-engine`

Own deterministic candidate identity, evidence verification, ontology/import/FIBO matching, duplicate detection, evidence comparison, recommendation normalization, provenance linkage semantics, and conversion to existing typed edits. It must not read HTTP uploads, call AI providers, manage users, or write source files.

### `web-server`

Own multipart intake, authorization, temporary files, extraction orchestration, OCR/provider adapters, in-memory task state, durable ingestion provenance storage for applied changes, progress/cancellation, call and time limits, exact-work cache, route contracts, and connection to shared staging. Provider-specific payloads stay behind the existing server-side provider boundary.

### `web-app`

Own upload controls, metadata forms, progress, safe evidence viewing, recommendation review, clarification, selection, and navigation to draft/proposal review. It must not construct RDF, decide semantic matches, verify excerpts, or convert recommendations into edits.

### Existing Modules Not Expanded

- `validation-engine` and `graph-diff` are reused through existing proposal services and change only if the audit finds a narrowly missing test hook.
- `cli` and `vscode-extension` do not change.
- `shared` does not change.
- No new Gradle module, server framework, database, queue, vector store, global React state framework, or apply path is planned.

## Affected Modules And Files

Exact names are pinned in Slice 0. Expected areas are:

- `core-types/src/main/kotlin/com/entio/core/`
  - one focused `DocumentIngestionContracts.kt`;
  - one focused `DocumentRecommendationContracts.kt`;
  - focused contract tests.
- `semantic-engine/src/main/kotlin/com/entio/semantic/`
  - `DocumentEvidenceVerifier.kt`;
  - `DocumentCandidateMatchingService.kt`;
  - `DocumentEvolutionService.kt`;
  - `DocumentRecommendationDraftTranslator.kt`;
  - minimal reuse changes to deterministic semantic/FIBO search and typed edit translation.
- `web-server/src/main/kotlin/com/entio/web/ingestion/`
  - intake and temporary-storage service;
  - durable ingestion provenance repository for applied ontology changes;
  - PDF, DOCX, text, and OCR extraction adapters;
  - task manager and exact-work cache;
  - provider-neutral document-analysis contract and an approved reuse or focused extension of the active OpenAI provider adapter;
  - ingestion web service and DTOs.
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
  - bounded route wiring only.
- `web-app/src/web/`
  - additive ingestion contracts, API calls, and query helpers.
- `web-app/src/workbench/document-ingestion/`
  - upload, progress, evidence, recommendation, and draft-impact components.
- existing project navigation and styles for one additive ingestion entry.
- deterministic fixtures under existing module test resources.
- one decision/completion record per slice under `docs/decisions/`.
- final README, `AGENTS.md`, and Phase 11 summary updates only after all implementation verification passes.

Any broader file set requires a plan amendment.

## Cross-Workflow Ontology Evolution

Phase 11 must support ontology evolution across separate ingestion workflows.

Example:

```text
Document 1
→ recommendations approved
→ ontology updated
→ supporting provenance retained

Later, Document 2
→ compared with the current ontology
→ compared with provenance retained from Document 1
→ recommends confirm, extend, revise, split, merge, conflict, or supersede
```

The second document does not need to be processed in the same task or batch as the first.

For every applied document-derived ontology change, Entio must retain durable provenance sufficient to show:

- the ontology element or assertion that was created or changed;
- the document and exact evidence that supported it;
- the recommendation and human decision that produced the change;
- the model, prompt, extraction method, and confidence used;
- the apply event and resulting ontology fingerprint.

Temporary task execution state may still be removed on restart or task deletion. Durable provenance for applied ontology changes must remain available across later workflows.

Durable provenance is workflow and audit data. It must not be written into Turtle or other ontology source files unless a later approved phase explicitly adds ontology annotations.

## Analysis Order

For tasks containing multiple documents:

1. Extract and analyze each document independently.
2. Produce one summary and candidate set per document.
3. Compare candidates across documents.
4. Detect supporting, duplicate, conflicting, and superseding evidence.
5. Produce the combined recommendation set.

The provider must not receive all documents as one unbounded prompt.

## Recommendation Outcome Rules

- `Confirm`: records additional supporting provenance and creates no ontology edit.
- `Reuse`: links a candidate to an existing entity; it may create instance assertions, but it must not create a duplicate schema entity.
- `Extend` and `Revise`: create typed edits only for changed fields or relationships.
- `Create`: creates only supported typed entities or assertions.
- `Split`, `Merge`, `Conflict`, and `Supersede`: require explicit human decisions before draft generation.
- Unsupported or insufficient recommendations remain review-only.

## Split And Merge Boundary

Phase 11 may identify and explain possible splits or merges.

A split or merge may enter the typed draft only when every required change maps to existing approved typed operations.

If it requires bulk reference migration, destructive deletion, equivalence semantics, unsupported dependency handling, or raw RDF, it remains review-only.

## Document Authority Rules

Authority, applicability, effective date, expiration date, amendment status, and supersession status are user-supplied metadata or explicit verified document facts.

AI may suggest these values, but they remain unconfirmed until accepted by the user.

A newer document does not automatically override an older document.

## Summary Grounding

Every factual summary item must reference one or more verified evidence blocks.

A summary item is accepted only when:

- every quote matches extracted text exactly;
- every named entity maps to a verified candidate;
- every relationship maps to verified evidence;
- unsupported narrative is removed or clearly marked as AI interpretation.

The summary is not itself evidence.

## Language Boundary

Phase 11 initially supports English-language documents.

Non-English or mixed-language documents are rejected or marked unsupported unless Slice 0 explicitly approves language detection, model support, OCR language packs, extraction behavior, and ontology matching for those languages.

## Encrypted Document Boundary

Password-protected or encrypted PDF and DOCX files are rejected in Phase 11.

Entio must not accept or retain document passwords.

## OCR Artifact Limits

Rendered OCR page images are temporary extraction artifacts.

Slice 0 must pin:

- maximum rendering DPI;
- maximum page dimensions;
- maximum total rendered-image bytes per task;
- cleanup timing;
- whether page images remain available after extraction for evidence review.

OCR images must be deleted when the task is deleted, expires, or the server restarts.

## Concurrency

Slice 0 must pin bounded concurrency rules. The default design is:

- one active ingestion execution per project;
- a fixed server-wide maximum of active ingestion tasks;
- one provider request at a time per task;
- one OCR page operation at a time per task unless a safe bounded alternative is approved;
- no new work after cancellation;
- deleted or superseded tasks cannot continue using OCR or provider resources.

## Exact-Work Cache Rules

Cached analysis may be reused only when every exact-work key component matches.

Authorization, evidence verification, ontology fingerprint checks, and current duplicate checks must still run before recommendation review or draft creation.

## Proposal Grouping

One ingestion task may create several ordered private-draft batches, but it produces one final proposal by default.

The user may exclude recommendation groups before submission.

Phase 11 must not automatically create several proposals from one ingestion task.

## Dependency Order

1. Slice 0: contract, dependency, security, and typed-operation audit.
2. Slice 1: neutral ingestion, evidence, and recommendation contracts.
3. Slice 2: safe intake, temporary lifecycle, and task orchestration.
4. Slice 3: deterministic text extraction and page-level OCR.
5. Slice 4: bounded document-analysis provider boundary and evidence verification.
6. Slice 5: ontology/FIBO matching, duplicate prevention, and iterative comparison.
7. Slice 6: recommendation review contracts and web workspace.
8. Slice 7: typed private-draft conversion and existing proposal integration.
9. Slice 8: security, accessibility, lifecycle, scale, and end-to-end gate.
10. Slice 9: final documentation and phase completion.

Each slice starts only after the prior slice is reviewed and present on the implementation branch. Slices must not be implemented in parallel because their contracts and safety gates are dependent.

## Implementation Slices

### Slice 0: Contract, Dependency, Security, And Typed-Operation Audit

#### Goal

Approve exact reuse points, dependencies, deployment assumptions, routes, permissions, and acceptance-test traceability before production code changes.

#### Allowed Files And Modules

- this spec and ExecPlan;
- one new `docs/decisions/phase-11-slice-0-contract-audit.md`;
- read-only inspection of all source, tests, lockfiles, and build files.

#### Forbidden Actions And Modules

- No production, fixture, test-code, build, dependency, or lockfile changes.
- No implementation while Slice 0 questions remain open.
- No edits to the Phase 11 scope without explicit instruction.
- No replacement, removal, or weakening of the active native assistant or its review-only proposal surface.

#### Expected Changes Or Output

The audit must record:

- exact core contract names, invariants, and JSON mapping;
- exact upload, task, evidence, review, reconsideration, and draft routes;
- exact project/user permission checks available in the current development identity boundary;
- temporary-directory root, ownership, cleanup, and symlink/path protections;
- approved PDFBox, Apache POI, Tesseract adapter/runtime, and license/version choices;
- whether Tesseract is bundled or administrator-provided and how the path is fixed;
- DOCX ZIP-bomb, external-relation, macro, image, and entry-count protections;
- the embedded-text reliability algorithm and deterministic fixture corpus;
- exact selected-model compatibility rules and structured response format;
- prompt versioning and exact-work cache key;
- provider timeout, retry, cancellation, redaction, and call-accounting behavior;
- existing local/import/FIBO search reuse points;
- existing private-draft, shared-stage, proposal, and typed-edit reuse points;
- supported SHACL edit matrix without adding new constraint semantics;
- document-viewer safety choice;
- field-level provenance retention through staging and proposal review;
- durable provenance storage design for applied document-derived changes, including schema, keys, authorization, retention, cleanup, and migration assumptions;
- cross-workflow comparison rules using current ontology content and durable prior provenance;
- per-document then cross-document analysis order;
- language boundary and OCR language-pack decision;
- encrypted PDF rejection behavior;
- OCR render DPI, dimensions, byte limits, and cleanup;
- task concurrency limits;
- one-task-to-one-final-proposal grouping rule;
- acceptance-criterion-to-slice-and-test traceability.

#### Tests

- Documentation and contract review.
- Dependency license and vulnerability review using repository-approved tooling.
- Small proof fixtures may be inspected, but no production or test code is committed in this slice.

#### Verification Commands

```bash
git diff --check
git status --short
```

#### Stop Conditions

- OCR requires an arbitrary user-selected executable, unrestricted network service, or unsupported deployment assumption.
- Safe document parsing requires a new service or process boundary not approved by the spec.
- The selected-model boundary cannot enforce fixed endpoints, structured output, and bounded input.
- Existing typed operations cannot represent a recommendation without raw RDF.
- Current identity cannot prevent cross-project document access.
- Temporary files cannot be isolated from ontology sources.
- Applied document-derived changes cannot retain durable provenance without writing provenance into ontology source files.
- Any required dependency has unacceptable licensing or unresolved critical security findings.

### Slice 1: Neutral Ingestion, Evidence, And Recommendation Contracts

#### Goal

Add minimal immutable product contracts and invariants without web, parser, provider, or UI details.

#### Allowed Files And Modules

- approved focused files in `core-types`;
- corresponding `core-types` tests;
- `docs/decisions/phase-11-slice-1-core-contracts.md`.

#### Forbidden Actions And Modules

- No semantic-engine, web-server, web-app, CLI, VS Code, or shared changes.
- No Ktor, provider, PDF/OCR, filesystem, Jena, or React types.
- No raw RDF or source-write contract.
- No new dependency.

#### Expected Changes Or Output

- Document identity, media type, checksum, authority, applicability, and processing status.
- Located block, extraction method, confidence, page geometry, and coordinate records.
- Evidence reference/type and exact excerpt invariants.
- Candidate category and stable identity inputs.
- Recommendation action, schema/instance category, match, ambiguity, conflict, dependency, confidence, and review status.
- Document summary highlight links.
- Review decision, durable provenance linkage, and draft-provenance records.
- Explicit fixed-state enums and deterministic ordering keys.
- Bounds enforced either in constructors or services as approved by Slice 0.

#### Tests

- Construction and invariant tests.
- Invalid offsets, confidence, coordinates, identities, action/category combinations, and evidence links.
- Deterministic equality and ordering.
- JSON-safe additive contract review.

#### Verification Commands

```bash
./gradlew :core-types:test
./gradlew :core-types:build
git diff --check
git status --short
```

#### Stop Conditions

- A neutral contract would depend on web/session/provider/parser types.
- Schema and instance outputs cannot be represented separately.
- Exact evidence cannot be linked without exposing temporary paths.
- Contracts require raw graph triples rather than existing typed intent.

### Slice 2: Safe Intake, Temporary Lifecycle, And Task Orchestration

#### Goal

Create authorized bounded ingestion tasks, store temporary artifacts safely, and establish the approved durable provenance repository boundary before adding extraction or provider execution.

#### Allowed Files And Modules

- approved ingestion files in `web-server`;
- minimal `Application.kt` route wiring;
- server tests and safe tiny intake fixtures;
- existing web-server build file only for already-approved intake support;
- `docs/decisions/phase-11-slice-2-intake-task-lifecycle.md`.

#### Forbidden Actions And Modules

- No semantic-engine, core contract, frontend, CLI, VS Code, or source-writing changes.
- No extraction, OCR, provider call, semantic matching, or draft conversion.
- No client-provided paths.
- No durable document store, external object store, or background queue beyond the Slice 0-approved minimal provenance repository.
- No document content or path in progress events or logs.

#### Expected Changes Or Output

- Multipart intake for approved media only.
- Streaming size enforcement before full buffering.
- Signature/type/extension agreement and SHA-256 checksum.
- Safe DOCX container preflight hooks.
- Server-generated document/task IDs and temporary directories.
- In-memory task manager with status, progress, cancellation, deletion, and restart cleanup hooks.
- Project/user authorization on every task and artifact lookup.
- Ten-document, 25-MB, and 500-page preflight limits where determinable.
- Same-project checksum duplicate detection without provider work.
- Minimal durable provenance repository for applied document-derived changes, isolated from ontology source files and authorized by project.

#### Tests

- Accepted media and metadata.
- Unsupported, mismatched, oversized, encrypted/preflight-invalid, unsafe filename, and too-many-document cases.
- Partial upload and storage failure cleanup.
- Symlink, traversal, and cross-project/user access attempts.
- Duplicate checksum behavior.
- Cancellation, deletion, shutdown cleanup, and safe progress/log assertions.
- Durable provenance repository authorization, retention, restart survival, and separation from temporary task cleanup.

#### Verification Commands

```bash
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

#### Stop Conditions

- Ktor cannot enforce the byte limit while streaming.
- Temporary storage can escape its server-owned root or overlap project files.
- Existing identity cannot authorize every task/artifact operation.
- Intake requires production persistence or a new queue.

### Slice 3: Deterministic Extraction And Page-Level OCR

#### Goal

Extract ordered located text from supported documents, using OCR only for PDF pages without usable embedded text.

#### Allowed Files And Modules

- approved extraction/OCR files under `web-server` ingestion;
- `web-server/build.gradle.kts` for Slice 0-approved parser/OCR dependencies;
- Gradle lock or verification metadata required by repository policy;
- deterministic extraction fixtures and tests;
- `docs/decisions/phase-11-slice-3-extraction-ocr.md`.

#### Forbidden Actions And Modules

- No core/semantic behavior changes, provider calls, matching, frontend, staging, or source writes.
- No OCR for pages that pass the embedded-text reliability test.
- No handwritten OCR or standalone images.
- No arbitrary executable path, shell command, remote OCR URL, macro, script, or external document relation.
- No silent guessing below confidence thresholds.

#### Expected Changes Or Output

- PDFBox per-page embedded text and deterministic reliability checks.
- Page rendering and fixed local Tesseract adapter for failed pages.
- POI DOCX paragraph, heading, and table-cell extraction in reading order.
- UTF-8 TXT/Markdown extraction and safe heading recognition.
- Stable block IDs, normalized offsets, extraction method/version, page, heading, exact text.
- OCR confidence, page dimensions, normalized coordinates, warnings, and page-image references.
- Enforcement of page, text, OCR-page, OCR-time, archive, render-DPI, rendered-image-byte, page-dimension, and decompression limits.
- Explicit rejection of password-protected or encrypted PDF and DOCX files.

#### Tests

- Text PDF, scanned PDF, and mixed PDF.
- Embedded text prevents OCR.
- Reliability boundaries, replacement characters, sparse text, and mixed scripts.
- DOCX headings, tables, external relationships, macros, malformed ZIP, and compression bomb.
- TXT/Markdown valid and invalid UTF-8.
- Stable page/block ordering and offsets.
- OCR coordinate normalization and confidence below 80/60.
- 200-page OCR, five-million-character, and ten-minute cancellation boundaries with fakes.
- Extraction failure and cleanup.

#### Verification Commands

```bash
./gradlew :web-server:test
./gradlew :web-server:build
./gradlew check
git diff --check
git status --short
```

#### Stop Conditions

- Approved libraries cannot provide reliable location or confidence data.
- OCR cannot be bounded or cancelled.
- A parser executes active content or follows external relationships.
- Mixed-page results cannot preserve deterministic document order.
- Required native/runtime packaging differs from Slice 0 approval.

### Slice 4: Bounded Analysis Boundary And Evidence Verification

#### Goal

Analyze extracted blocks through a narrow selected-model boundary and accept only server-verifiable evidence.

#### Allowed Files And Modules

- approved provider-neutral ingestion analysis files in `web-server`;
- one approved provider adapter behind the existing credential/model boundary;
- `semantic-engine/DocumentEvidenceVerifier.kt` and tests;
- minimal existing credential/model service accessors approved in Slice 0;
- prompt fixtures and provider fakes;
- `docs/decisions/phase-11-slice-4-analysis-evidence.md`.

#### Forbidden Actions And Modules

- No document-driven changes to ordinary assistant chat behavior, tool calling, autonomous loop, external URL selection, raw RDF, staging, or apply.
- No credential, filesystem path, permission, or complete unbounded document in a prompt.
- No provider-authored excerpt stored as truth.
- No silent model fallback.
- No more than two transient retries or twenty total task calls.

#### Expected Changes Or Output

- Versioned multi-stage analysis request/response contracts.
- Per-document analysis followed by bounded cross-document comparison; no unbounded combined-document prompt.
- Eligibility check for the current user's verified selected model.
- Bounded excerpt packing and deterministic opaque block IDs.
- Fixed system instruction treating documents as untrusted data.
- Provider timeout, cancellation, retry classification, rate/call accounting, and redaction.
- Kotlin exact-offset excerpt resolution and rejection of altered/invented quotes.
- Stable candidate identity, evidence-type validation, strict summary grounding, interpretation labels, and safe failures.
- Exact-work key including checksum, ontology fingerprint, model, prompt, extractor, and metadata.

#### Tests

- Explicit entity, relationship, value, rule, and ambiguity extraction through fakes.
- Missing/unverified/incompatible model.
- Exact, altered, invented, out-of-range, and cross-document excerpts.
- Multi-passage evidence and unsupported response fields.
- Prompt injection requesting tools, secrets, permissions, network access, or rule bypass.
- Provider timeout, transient retry, permanent failure, malformed output, cancellation, and call limit.
- Secret, payload, path, and excerpt redaction.
- Identical completed work avoids repeat calls.

#### Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

#### Stop Conditions

- Provider structured output cannot be validated deterministically.
- Fixed endpoints and current-user credentials cannot be preserved.
- Evidence verification would require trusting model-generated quotes.
- The task needs an autonomous agent or unbounded context.
- Historical AI proposal code must be reactivated instead of a narrow ingestion boundary.

### Slice 5: Ontology Matching, Duplicate Prevention, And Iterative Comparison

#### Goal

Turn verified candidates into deterministic matches and evidence-aware recommendations without creating edits.

#### Allowed Files And Modules

- approved focused services and tests in `semantic-engine`;
- minimal additive reuse changes to current semantic search, import, FIBO, staging/proposal normalization APIs;
- server orchestration needed to provide authorized current-session evidence;
- deterministic ontology/evidence fixtures;
- `docs/decisions/phase-11-slice-5-matching-evolution.md`.

#### Forbidden Actions And Modules

- No new ontology index, embeddings, vector database, external catalog, frontend, typed-draft conversion, or source write.
- No label-only duplicate decision.
- No automatic ambiguity, conflict, split, merge, or supersession decision.
- No change to FIBO assets.

#### Expected Changes Or Output

- Ordered search of applied local, imports, current work, same-task, durable prior provenance, and curated FIBO scopes.
- Canonical IRI and normalized typed-operation duplicate identity.
- Deterministic candidate rankings and match reasons.
- Confirm, reuse, extend, revise, create, split, merge, conflict, supersede, insufficient, and unsupported recommendations.
- Confirm recommendations record provenance only and create no ontology edit.
- Split and merge remain review-only when existing typed operations cannot represent the complete safe change.
- Authority/applicability/date-aware comparison that never treats recency as authority.
- Conflict alternatives with affected ontology entities and evidence links.
- Mandatory-clarification flags and deterministic ordering.
- Reprocessing behavior when an exact-work key changes.

#### Tests

- Local, imported, draft/staged/proposal, durable prior-provenance, and FIBO matches.
- Ambiguous match and no-match creation recommendation.
- Duplicate candidates within and across documents/tasks, including separate workflows after restart.
- Confirm, extend, revise, split, merge, conflict, and explicit supersession.
- Date, expiration, jurisdiction, and business-area applicability.
- Newer document without authority does not supersede.
- FIBO search stays within pinned curated modules.
- Stable results across repeated runs.

#### Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew :semantic-engine:build
./gradlew :web-server:test
git diff --check
git status --short
```

#### Stop Conditions

- Matching requires unbounded external retrieval or a new index.
- Canonical duplicate checks cannot reuse existing entity/edit identities.
- Durable provenance cannot support authorized cross-workflow comparison without a new unapproved persistence design.
- A recommendation needs unsupported OWL, SHACL, or raw RDF semantics.

### Slice 6: Recommendation Review Contracts And Web Workspace

#### Goal

Expose authorized bounded tasks and let users safely inspect and decide recommendations without constructing ontology statements in React.

#### Allowed Files And Modules

- approved ingestion DTOs, services, and routes in `web-server`;
- additive web contracts/API/query files;
- new focused `web-app/src/workbench/document-ingestion/` components;
- minimal project navigation and style changes;
- backend, Vitest, accessibility, and browser tests;
- `docs/decisions/phase-11-slice-6-review-workspace.md`.

#### Forbidden Actions And Modules

- No semantic policy or typed edit construction in TypeScript.
- No active document HTML, macro, external link, or script rendering.
- No full document body in list/progress endpoints.
- No global store dependency, document-management UI, CLI, or VS Code changes.
- No stage/apply action yet.

#### Expected Changes Or Output

- Bounded task/document/summary/evidence/recommendation read contracts.
- Upload and authority/applicability form.
- Progress, cancellation, deletion, and safe error states.
- Safe PDF page or extracted-text evidence viewer with supported highlighting.
- Separate ontology-structure and business-fact review.
- Match comparison, conflict evidence, confidence/evidence labels, mandatory clarification, and prior-workflow provenance for ontology content affected by the new document.
- Accept, reject, edit supported fields, merge duplicates, rematch, target source, clarification, and reconsideration controls.
- Draft-impact preview that remains read-only until Slice 7.
- Keyboard, screen-reader, focus, loading, and responsive behavior.

#### Tests

- Contract serialization and authorization.
- Pagination/truncation and no body/excerpt leakage in task lists or progress.
- All review decisions and invalid transitions.
- Safe rendering of Markdown, HTML-like text, links, filenames, and bidi/control characters.
- PDF page and extracted-text navigation.
- Visible evidence type, OCR, confidence, schema/instance, conflict, and clarification labels.
- Reconsideration limit and stale-result handling.
- Keyboard and accessible-name assertions.

#### Verification Commands

```bash
./gradlew :web-server:test
cd web-app
npm test
npm run build
git diff --check
git status --short
```

#### Stop Conditions

- Safe viewing requires executing document content.
- Browser code must infer ontology meaning or author graph statements.
- Existing query/navigation architecture cannot support the workspace additively.
- A route would expose cross-project data, server paths, raw provider payloads, or unbounded text.

### Slice 7: Typed Private-Draft Conversion And Proposal Integration

#### Goal

Convert accepted current recommendations into existing typed operations and reuse the complete current proposal workflow.

#### Allowed Files And Modules

- `semantic-engine/DocumentRecommendationDraftTranslator.kt` and focused tests;
- minimal approved additions to existing typed translators or provenance metadata;
- ingestion staging orchestration in `web-server`;
- existing `StagingWorkflowService`, proposal planner, validation/reasoning/SHACL adapters where additive hooks are required;
- review workspace draft submission controls;
- integration fixtures and tests;
- `docs/decisions/phase-11-slice-7-typed-draft.md`.

#### Forbidden Actions And Modules

- No raw RDF, direct graph mutation, source write, separate proposal type, separate apply path, auto-approval, or partial atomic batch stage.
- No unsupported OWL/SHACL edit.
- No more than 100 accepted edits or 20 edits per batch.
- No CLI, VS Code, database, or durable-history expansion beyond the approved applied-change provenance repository.

#### Expected Changes Or Output

- Server-side translation matrix for every approved recommendation/typed operation.
- Recheck of authorization, evidence, OCR confirmation, ambiguity, conflicts, matches, writable source, dependencies, duplicates, and graph fingerprint.
- Up to five ordered all-or-nothing batches.
- Provenance linking document, block/excerpt, task, model/prompt, extraction method, confidence, recommendation decision, and typed item.
- On successful apply, durable provenance records linked to the resulting ontology entities/assertions and apply fingerprint.
- Confirm outcomes persist supporting provenance without creating no-op draft items.
- Existing shared staging and private-draft preview.
- Existing semantic diff, validation, reasoning, SHACL, repair, proposal review, human approval, atomic apply, reload verification, and rollback.
- Clear unsupported/stale/blocked feedback without losing review decisions.

#### Tests

- Every supported typed mapping and unsupported mapping.
- Schema and fact batch separation.
- Low-confidence, ambiguous, conflict, split, merge, supersede, and unresolved-source gates.
- Duplicate and stale graph/evidence/model/prompt checks.
- Batch all-or-nothing behavior, 20-edit batch limit, and 100-edit task limit.
- Provenance retained through staging and proposal review, persisted after successful apply, and not written as ontology annotation.
- Validation failure and repair; reasoning and SHACL impact.
- No source changes before approval.
- Existing apply, reload verification, and rollback regression.

#### Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew :web-server:test
./gradlew test
./gradlew build
cd web-app
npm test
npm run build
git diff --check
git status --short
```

#### Stop Conditions

- A requested mapping needs raw RDF or unsupported semantic behavior.
- Provenance cannot remain attached through the existing workflow without changing ontology output.
- Existing staging cannot accept an all-or-nothing typed batch.
- Integration would weaken stale-proposal, approval, apply, reload, or rollback checks.

### Slice 8: Security, Accessibility, Lifecycle, Scale, And End-To-End Gate

#### Goal

Prove the completed workflow is bounded, isolated, usable, and unable to bypass Entio's trust boundaries.

#### Allowed Files And Modules

- focused fixes only in files introduced or explicitly modified by Slices 1–7;
- deterministic security and scale fixtures;
- web-server integration tests, Vitest tests, and Playwright tests;
- test configuration already used by the repository;
- `docs/decisions/phase-11-slice-8-verification.md`.

#### Forbidden Actions And Modules

- No new feature behavior, dependency, module, persistence layer, or limit increase.
- No production shortcuts for tests.
- No disabling flaky, security, accessibility, or scale tests.
- No live provider requirement in deterministic CI.

#### Expected Changes Or Output

- Full acceptance-criterion traceability.
- Deterministic fake provider and fake OCR path for CI plus separately documented real-adapter smoke procedure.
- Isolation, injection, archive/parser, temporary-file, redaction, cancellation, and restart-cleanup proof.
- Boundary fixtures for all numeric limits.
- Accessible complete browser workflow.
- Existing non-ingestion staging, proposal, reasoning, SHACL, map, settings, CLI, and VS Code regressions remain green.

#### Tests

- Every required scenario in the scope and feature spec.
- Cross-user/project access and enumeration resistance.
- Path traversal, symlink, malicious filename, macro, external relation, archive bomb, malformed parser input, prompt injection, provider payload injection, and log redaction.
- File/page/text/OCR/candidate/evidence/call/time/edit bounds.
- Cancellation at extraction, analysis, matching, and draft preparation.
- Server restart cleanup, durable provenance survival, cross-workflow comparison after restart, and exact-work reuse.
- Keyboard-only and screen-reader-oriented browser assertions.
- End-to-end text PDF, scanned PDF, mixed PDF, DOCX, TXT/Markdown, conflict, proposal/apply/rollback, and later-document revision of an earlier applied document-derived ontology change.

#### Verification Commands

```bash
./gradlew clean test
./gradlew build
./gradlew check
cd web-app
npm ci
npm test
npm run build
npm run test:e2e
cd ../vscode-extension
npm ci
npm test
git diff --check
git status --short
```

Run the approved dependency/license and vulnerability checks recorded by Slice 0. Run the real PDF/OCR/provider smoke suite only in its approved local environment; deterministic CI must not require credentials.

#### Stop Conditions

- Any authorization, path, injection, secret, source-write-before-approval, or cross-project failure.
- Any required limit cannot be enforced deterministically.
- Any acceptance scenario lacks automated coverage without a documented and approved reason.
- Existing core, CLI, VS Code, web, proposal, apply, reload, or rollback regression fails.

### Slice 9: Final Documentation And Phase Completion

#### Goal

Record the verified delivered boundary and update current repository status only after every prior gate passes.

#### Allowed Files And Modules

- `README.md`;
- `AGENTS.md`;
- `docs/phase-summaries/phase-11-summary.md`;
- this spec and ExecPlan status;
- exact architecture/ADR links identified by earlier slices;
- `docs/decisions/phase-11-slice-9-completion.md`.

#### Forbidden Actions And Modules

- No production, dependency, fixture, or test changes.
- No completion claim with failing or skipped required verification.
- No scope expansion or future-phase promises.

#### Expected Changes Or Output

- Spec and ExecPlan status changed to implemented only after verification.
- Phase 11 summary lists delivered behavior, bounds, dependencies, lifecycle, security model, verification, and known limitations.
- README and AGENTS accurately describe the active native ontology assistant and the narrower Phase 11 document-analysis extension.
- Deferred production document/task storage, broader formats, handwritten OCR, external indexing, and durable history beyond applied-change provenance remain explicit.

#### Tests

- Documentation consistency and link review.
- Acceptance-criterion traceability review against Slice 8 results.

#### Verification Commands

```bash
git diff --check
git status --short
```

#### Stop Conditions

- Any prior slice is incomplete.
- Required verification is failing or lacks recorded evidence.
- Documentation would overstate durability, format support, AI authority, or security.

## Test Plan

### Unit Tests

- immutable contract invariants;
- media and metadata validation;
- embedded-text reliability;
- UTF-8 and DOCX safety;
- OCR confidence and coordinates;
- exact excerpt verification;
- candidate identity and deterministic ordering;
- match ranking and canonical duplicate detection;
- authority/applicability comparison;
- recommendation-to-typed-edit translation;
- bounds, retries, cache keys, and state transitions.

### Integration Tests

- multipart intake through task completion;
- temporary storage and cleanup;
- extraction adapters with deterministic fixtures;
- fake provider analysis and evidence rejection;
- local/import/FIBO/current-work matching;
- multi-document conflict and supersession;
- authorization and isolation;
- typed staging, preview, validation, reasoning, SHACL, proposal, apply, reload, and rollback.

### Frontend Tests

- upload and metadata forms;
- task progress, cancellation, deletion, and errors;
- safe evidence viewer;
- schema/fact separation;
- summary-to-evidence/recommendation links;
- review decisions, clarification, reconsideration, and batching;
- accessibility, focus, keyboard, and responsive layout;
- stale and blocked states.

### Security And Scale Tests

- parser and archive attacks;
- traversal, symlink, filename, media mismatch, and cross-project attempts;
- prompt injection and malformed provider output;
- credential, path, payload, and excerpt redaction;
- every specified numeric boundary;
- cancellation and timeout at each long-running stage;
- no source write before approval.

### End-To-End Tests

- embedded-text PDF to approved proposal;
- scanned and mixed PDF with OCR review;
- DOCX and TXT/Markdown evidence navigation;
- local/import/FIBO reuse and no-match creation;
- iterative revision, conflict, and explicit supersession;
- rejected/edited recommendations and batch draft construction;
- validation repair and final apply/reload/rollback.

## Full Verification Commands

```bash
./gradlew clean test
./gradlew build
./gradlew check

cd web-app
npm ci
npm test
npm run build
npm run test:e2e

cd ../vscode-extension
npm ci
npm test

git diff --check
git status --short
```

Slice 0 must add the exact approved dependency, license, vulnerability, OCR runtime, and real-provider smoke commands. Tests must use fake OCR/provider adapters unless explicitly running the separately controlled smoke suite.

## Rollback Notes

- Every slice is a focused commit and must remain reversible without resetting or discarding unrelated user work.
- Until Slice 7, removing the ingestion routes and temporary task state leaves all ontology behavior unchanged.
- Slice 7 reuses existing staging; rollback removes only ingestion-to-typed-edit translation and its provenance adapter, not shared staging or proposal behavior.
- New dependencies are confined to `web-server`; reverting Slice 3 removes parser/OCR dependencies and lock/verification changes together.
- Temporary task directories are not ontology state and may be deleted by the approved cleanup command recorded in Slice 0.
- Durable provenance records for successfully applied document-derived changes are not removed by temporary task cleanup and must follow the approved retention rules.
- If a provider or OCR adapter is disabled, completed ontology proposals remain ordinary existing proposals; incomplete ingestion tasks become unavailable and must not be auto-resumed.
- Applied ontology changes continue to rely on existing atomic apply, reload verification, and rollback. Phase 11 adds no second recovery mechanism.

## Risks And Assumptions

- OCR quality varies by scan quality, language, layout, and installed language data. Confidence gates reduce but do not eliminate reviewer effort.
- PDF and DOCX parsers process hostile input. Dependency pinning, archive limits, isolation, and prompt cleanup are mandatory.
- A local Tesseract runtime complicates packaging. Slice 0 must choose a fixed supported deployment model.
- Provider output can be incomplete or wrong. Exact evidence verification and Kotlin-owned matching are the core safety boundary.
- The selected model may not support the required structured contract. Analysis remains blocked rather than silently switching.
- In-memory task state means restart loses incomplete tasks and temporary review state. Durable provenance for successfully applied document-derived ontology changes must survive restart.
- Ten large documents can approach memory and time limits. Streaming intake, temporary files, bounded excerpts, cancellation, and scale fixtures are required.
- The active assistant and document-ingestion analysis have different evidence contracts. Slice 0 must identify which credential, verified-model, context, and OpenAI adapter behavior can be reused without coupling document evidence to conversational proposal state.
- Existing typed operations may cover fewer SHACL recommendations than AI can describe. Unsupported suggestions remain review-only.
- Browser-native PDF behavior differs by browser. The safe viewer choice must have deterministic fallback to extracted text.

## Assumptions To Confirm In Slice 0

- The repository accepts PDFBox, Apache POI, and the chosen Tesseract adapter licenses.
- A fixed local OCR runtime is available in supported development/test environments.
- Existing current-user provider settings can authorize analysis without returning secrets to the browser.
- Current staging can retain workflow provenance without serializing it into Turtle.
- A minimal durable provenance repository can persist applied document-derived evidence across workflows without becoming a production document store.
- The pinned FIBO catalog and current semantic search provide sufficient bounded reuse search.
- Current development identity can enforce project-scoped ingestion permissions.

## Definition Of Done

Phase 11 is done only when:

- the feature spec, ExecPlan, and Slice 0 decisions are approved;
- all ten slices are implemented in order and have completion records;
- every acceptance criterion maps to passing automated tests or an explicitly approved controlled smoke test;
- all supported documents extract located evidence under the stated limits;
- AI summaries and recommendations contain only Kotlin-verified evidence links;
- matching and duplicate prevention cover every approved scope deterministically;
- a later document can revise or challenge ontology content created from an earlier completed workflow, including after server restart, using durable prior provenance;
- review and clarification gates prevent ambiguous or low-confidence conversion;
- accepted items become only existing supported typed operations;
- no ingestion route writes ontology sources or bypasses human approval;
- existing validation, reasoning, SHACL, proposal, apply, reload, and rollback paths pass;
- security, accessibility, scale, cancellation, cleanup, and isolation gates pass;
- README, AGENTS, spec status, ExecPlan status, and Phase 11 summary match the delivered behavior;
- `./gradlew clean test`, `./gradlew build`, `./gradlew check`, web tests/build/E2E, VS Code tests, and repository diff checks pass.

## Boundary Check

- The plan implements only the approved Phase 11 document-ingestion workflow.
- It avoids automatic application, raw RDF, production document persistence, autonomous agents, new catalogs, unbounded indexing, and unrelated product surfaces while allowing narrowly scoped durable provenance for applied document-derived changes.
- It preserves module direction: neutral contracts in `core-types`, semantics in `semantic-engine`, orchestration in `web-server`, and presentation in `web-app`.
- It keeps `shared`, CLI, and VS Code unchanged.
- It reuses existing RDF, OWL, SHACL, FIBO, staging, proposal, apply, reload, and rollback behavior.
- It adds only narrowly approved parsing/OCR dependencies after a mandatory audit and does not create a custom document, RDF, OWL, or SHACL framework.
- It preserves the active native assistant, treats documents and provider output as untrusted, and keeps every ontology change under typed validation and human control.
