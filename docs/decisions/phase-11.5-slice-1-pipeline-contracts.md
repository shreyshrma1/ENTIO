# Phase 11.5 Slice 1 Completion: Pipeline Contracts

**Status:** Complete

**Branch:** `feature/phase-11-5-slice-1-pipeline-contracts`

**Commit:** `Add Phase 11.5 pipeline contracts`

## Slice

Slice 1 implements the provider-neutral contracts required by the approved
Phase 11.5 pipeline. It depends only on the approved and locally merged Slice 0
contract audit.

## Goal

Represent each analysis stage and each connected recommendation as immutable
Kotlin data without introducing provider, server, browser, parser, filesystem,
Jena, or raw-RDF types into `core-types`.

## Implemented Contracts

The slice adds:

- every approved stage and terminal state;
- stable stage records with timing, progress, model, version, attempt, safe-code,
  input-hash, and output-hash fields;
- the approved prompt, request-schema, and response-schema version constants;
- discovery kinds plus business/metadata, assertion, and individual
  classifications;
- evidence-grounded discovery records;
- connected-model items with ordered model-local references;
- reconciliation records and explicit human-decision gates for conflicts and
  supersession claims;
- ontology-alignment decisions with advised targets and graph fingerprints;
- critic findings and final dispositions;
- evidence, modeling, and ontology-fit confidence dimensions, with Kotlin
  calculating overall confidence as their minimum;
- the exact `new:<kind>:<localName>` temporary-reference grammar;
- the approved executable operation kinds without raw graph statements;
- ordered operation dependencies, declaration-before-use checks, expanded
  typed-edit counts, and atomic recommendation limits;
- review-only findings, grouped recommendation statuses, blockers, and
  individual-creation confirmation gates;
- a deterministic coverage ledger with one outcome per verified discovery;
- final-plan bounds and exact critic-disposition accounting;
- grouped reviewer decision records; and
- constants for every new Phase 11.5 execution and response limit.

The final-plan contract permits a valid no-change result when all verified
discoveries are intentionally classified as metadata, duplicates,
illustrative examples, unsupported content, or rejected with rationale.

## Files

- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/DocumentAnalysisPipelineContractsTest.kt`
- `docs/decisions/phase-11.5-slice-1-pipeline-contracts.md`

No existing Phase 11 contract required an incompatible change.

## Tests

The focused test suite covers:

- every stage paired with every valid stage state;
- provider-backed and deterministic metadata separation;
- invalid timing, model, attempt, and confidence values;
- independent content and discovery classifications;
- required evidence and individual classifications;
- ordered connected-model declarations and backward-only references;
- conflict and supersession human-decision rules;
- every temporary-reference kind and invalid grammar;
- unresolved, forward, and over-limit connected operations;
- discovery, model-item, recommendation, and attempt boundaries;
- review-only recommendation invariants;
- unresolved critic and individual-confirmation blockers;
- valid no-change plans;
- exact coverage and critic disposition accounting; and
- absence of provider, Ktor, Jena, parser, filesystem, and UI field types.

## Verification

The generated duplicate test class left by an interrupted earlier compile was
removed only through Gradle's `:core-types:clean` task. It was untracked build
output and not a source or repository change.

Commands run successfully before commit:

```bash
./gradlew :core-types:clean :core-types:test
./gradlew :core-types:build
git diff --check
git status --short
```

The focused suite completed with 79 tests, including 11 new pipeline-contract
tests. The build completed without adding a dependency or changing build
configuration.

The same required verification commands are run again after the local
non-fast-forward merge.

## Decisions And Assumptions

- Administrative metadata is a content classification independent of discovery
  kind. For example, an effective-date field may be an administrative
  attribute without being forced into a generic metadata kind.
- Every document-derived individual requires explicit creation confirmation.
  Illustrative, ambiguous, or unknown classification remains recorded; changing
  such an individual to production requires the separate production
  reclassification confirmation.
- A blocked recommendation may retain all operations and findings so users can
  understand the complete proposed unit. Valid operations are never silently
  extracted.
- Temporary references contain no final IRI. Later semantic verification and
  translation slices resolve them using the existing collision-checked IRI
  generator.
- Complex rules can be represented in the connected model but must remain
  eligible for review-only handling when they cannot use an approved typed
  operation.

## Commit Record

This artifact is included in the focused commit
`Add Phase 11.5 pipeline contracts` on
`feature/phase-11-5-slice-1-pipeline-contracts`. The immutable commit hash is
recorded in Git history.
