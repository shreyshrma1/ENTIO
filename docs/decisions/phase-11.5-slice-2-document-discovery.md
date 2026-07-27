# Phase 11.5 Slice 2 Completion: Document Discovery

**Status:** Complete

**Branch:** `feature/phase-11-5-slice-2-document-discovery`

**Commit:** `Implement document discovery stage`

## Slice

Slice 2 implements the ontology-blind, per-document discovery stage defined by
the approved Phase 11.5 ExecPlan. It depends on the neutral Slice 1 contracts.
It does not perform connected modeling, reconciliation, ontology matching,
critique, final planning, staging, or source writes.

## Goal

Discover the full meaning present in each bounded document without allowing the
current ontology to narrow the model's observations. Verify every evidence
claim against server-held extracted text before a discovery may reach a later
stage.

## Implementation

The new discovery path provides:

- one strict provider call per document;
- the approved discovery prompt, request-schema, and response-schema versions;
- a dedicated request that contains document authority/applicability metadata,
  opaque document and block IDs, located text, extraction method, extractor
  version, OCR confidence, and no ontology or edit fields;
- whole-block deterministic packing under the 60,000-character instruction and
  input limit;
- explicit included-block IDs and omitted-block counts;
- an incomplete terminal stage whenever any document block is omitted;
- a strict OpenAI Responses JSON schema separate from the Phase 11 candidate
  schema;
- discovery kinds, business/metadata classification, assertion classification,
  and individual classification;
- model-local related IDs resolved to stable Kotlin-generated discovery IDs;
- exact evidence verification through the existing semantic-engine verifier;
- safe per-item skip records for altered, invented, cross-document, malformed,
  and unresolved-related evidence;
- deterministic discovery ordering, hashes, stage timing, model/version
  metadata, and provider attempt counts;
- exact-work caching without storing credentials or provider payloads;
- task-wide limits of 200 discoveries per document, 2,000 per task, three
  automatic retries, and twenty provider attempts; and
- batch discovery that calls each document exactly once in stable document
  order.

The existing Phase 11 single-stage service remains readable during this slice.
The approved Slice 8 orchestrator will activate the new pipeline and remove the
old path from production use.

## Files

- `semantic-engine/src/test/kotlin/com/entio/semantic/DocumentEvidenceVerifierTest.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentAnalysisServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClientTest.kt`
- `docs/decisions/phase-11.5-slice-2-document-discovery.md`

No new file duplicated an existing ingestion service, provider adapter,
evidence verifier, or test responsibility. No build or dependency file changed.

## Tests

Focused and regression tests prove:

- discovery of concepts, definitions, relationships, controls, rules, facts,
  values, and illustrative examples;
- administrative document-control values remain metadata;
- possible individuals retain the required four-value classification;
- stable discovery IDs and related-discovery references;
- exact evidence succeeds while altered, invented, ambiguous, and
  cross-document evidence fails safely;
- OCR page, section, extraction-method, and confidence metadata survives
  evidence verification;
- prompt injection remains quoted block data and cannot alter the system
  instruction;
- discovery requests contain no ontology context, source selection, final IRI,
  domain, range, typed edit, or recommendation field;
- whole oversized blocks are omitted rather than truncated, and any omission
  makes the result ineligible for later stages;
- malformed versions, duplicate IDs, response overflow, retry exhaustion,
  cancellation, missing model readiness, and task-wide provider-attempt limits
  fail with safe codes;
- exact work is cached; and
- a batch makes exactly one discovery call per document.

The final clean test run completed 209 semantic-engine tests and 171 web-server
tests.

## Verification

Commands completed successfully before commit:

```bash
./gradlew :semantic-engine:test
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

Before the final gate, Gradle clean tasks removed stale untracked incremental
class copies with names ending in ` 2.class`. Those files existed only under
ignored module `build/` directories; no source or user file was removed.

The required commands are run again after the local non-fast-forward merge.

## Decisions And Assumptions

- The 60,000-character prompt limit includes the discovery system instruction
  and the serialized request. Response-schema text is transport metadata and is
  bounded separately by the one-million-character response limit.
- Blocks are atomic for discovery packing. Entio never truncates a block and
  presents the partial text as complete.
- Partial discoveries may remain visible when packing is incomplete, but the
  stage record is `Incomplete` and `eligibleForLaterStages` is false.
- Provider-local discovery IDs are temporary cross-reference handles only.
  Stable IDs are derived by Kotlin from the document checksum, kind, normalized
  description, and verified evidence identities.
- Evidence classification is derived from the approved assertion
  classification. Provider text never decides whether a quotation is valid.
- Automatic retry and provider-attempt counters are task-wide even before the
  full Slice 8 orchestrator is connected.

## Commit Record

This artifact is included in the focused commit
`Implement document discovery stage` on
`feature/phase-11-5-slice-2-document-discovery`. The immutable commit hash is
recorded in Git history.
