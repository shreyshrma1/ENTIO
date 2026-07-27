# Phase 11.5 Slice 4 Completion: Document Reconciliation

**Status:** Complete

**Branch:** `feature/phase-11-5-slice-4-reconciliation`

**Commit:** `Add document reconciliation stage`

## Slice

Slice 4 implements the cross-document and prior-applied-provenance
reconciliation stage from the approved Phase 11.5 ExecPlan. It consumes
verified discoveries, the completed local connected model, bounded document
authority context, and project-scoped provenance summaries.

It does not resolve conflicts, approve supersession, align with the current
ontology, critique the model, produce a final plan, stage edits, or write
sources.

## Goal

Explain when documented meanings duplicate, support, refine, conflict with, or
apply in a different context from one another. Preserve explicit supersession
claims for human review without treating a newer effective date as authority.

## Implementation

The reconciliation path provides:

- exactly one reconciliation call for every task, including a one-document
  task and a task with no prior provenance;
- a strict provider request and response schema separate from discovery,
  connected modeling, and consolidation;
- verified discovery summaries that preserve content classification,
  assertion classification, related discovery IDs, evidence IDs, and exact
  excerpts;
- the complete validated connected model with model-local references;
- bounded document authority fields for status, effective and expiration date,
  jurisdiction, business area or unit, related document, and language;
- up to twenty-five recent project-scoped prior applied-provenance summaries;
- prior summaries limited to record and document identity, accepted action,
  confidence, bounded evidence excerpts, optional normalized typed-operation
  identity, applied time, and resulting ontology fingerprint;
- comparisons for discovery-to-discovery, model-to-model, and
  model-to-prior-provenance relationships;
- duplicate, alternate-label, support, refinement, conflict,
  supersession-claim, and context-specific result kinds;
- deterministic validation that every participant, evidence ID, and prior
  provenance ID was supplied by Entio and is reachable from that comparison;
- stable Kotlin-generated reconciliation identities and deterministic result
  ordering;
- mandatory human-decision flags for conflicts and supersession claims;
- deterministic rejection of supersession claims that rely only on dates and
  lack explicit supersession evidence or accepted prior supersession
  provenance;
- a 300-record response limit, twenty participants per record, a 60,000
  character request limit, one exact retry per logical call, and task-wide
  provider bounds; and
- a stage record containing timing, selected model, prompt and schema
  versions, hashes, attempts, and result counts.

## Project-Scoped Prior Provenance

`AppliedDocumentProvenanceRepository` now exposes one internal bounded read
adapter for reconciliation. It first selects the most recent records, then
returns their summaries in stable record-ID order.

The adapter reuses the repository's existing project registry validation and
hashed project directory. It cannot read records from another project through
the requested project ID. It does not load a general document store, temporary
files, complete prior documents, page images, prompts, or provider responses,
and it adds no persistence format.

Existing Phase 11 provenance remains readable because the adapter summarizes
the existing version-one `AppliedDocumentProvenance` records without requiring
new fields.

## Files

- `web-server/src/main/kotlin/com/entio/web/ingestion/AppliedDocumentProvenanceRepository.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/AppliedDocumentProvenanceRepositoryTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentAnalysisServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClientTest.kt`
- `docs/decisions/phase-11.5-slice-4-reconciliation.md`

No core contract, module, dependency, persistence format, general document
store, alternate provider adapter, or duplicate completion artifact was added.

## Tests

Focused and regression tests prove:

- the same meaning across two documents produces a duplicate relationship;
- incompatible reviewer requirements remain an unresolved conflict with a
  required human decision;
- jurisdiction and business-area differences remain context-specific;
- explicit “supersedes” evidence permits a supersession claim while a newer
  effective date by itself fails safely;
- one-document tasks call reconciliation once with no prior records and once
  with applicable prior records;
- a model item can be compared with bounded prior applied provenance;
- Phase 11 provenance remains readable after repository restart and temporary
  document cleanup;
- project A provenance is inaccessible through project B or an unknown
  project;
- recent prior summaries are bounded and deterministically ordered;
- reconciliation requests contain authority context but no ontology context,
  target source, complete prior document, or raw provider response;
- response fields are strict and reject undeclared conflict-resolution output;
  and
- credentials remain outside request bodies.

## Verification

Commands completed successfully before commit:

```bash
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

Gradle clean removed only ignored generated `web-server/build` output after the
local incremental compiler emitted a duplicate class ending in ` 2.class`. The
clean full test and build run passed.

The required commands are run again after the local non-fast-forward merge.

## Decisions And Assumptions

- Reconciliation receives verified discovery meaning and bounded exact
  evidence, not complete raw documents.
- The twenty-five-record prior summary bound favors most recently applied
  records while preserving deterministic provider input order.
- Prior provenance IDs remain opaque comparison participants. The provider
  cannot use them to access another project or retrieve more data.
- A supersession claim must cite evidence containing explicit supersession,
  replacement, or revocation language, or reference accepted prior provenance
  whose action was supersession. Dates alone never satisfy this check.
- Reconciliation may explain a conflict but cannot return a resolution field.
- Three logical calls are reserved for ontology alignment, modeling critique,
  and final planning. The Slice 8 orchestrator will own the shared task ledger.

## Commit Record

This artifact is included in the focused commit
`Add document reconciliation stage` on
`feature/phase-11-5-slice-4-reconciliation`. The immutable commit hash is
recorded in Git history.
