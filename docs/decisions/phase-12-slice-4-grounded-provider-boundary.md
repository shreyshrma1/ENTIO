# Phase 12 Slice 4: Grounded Provider Boundary

Status: Complete on 2026-07-31

## Decision

Phase 12 grounded interpretation uses a provider-neutral service and a separate
OpenAI Responses adapter capability. The service groups at most 40 ordered
candidates, preserves each candidate's exact evidence and frozen top-20
retrieval result, requires complete candidate coverage, and accepts reuse or
extension only through an exact server-issued selection ID.

The OpenAI request uses the current verified model ID, disables storage and
tools, treats document and ontology text as untrusted data, and requires a
strict JSON schema containing semantic meaning rather than final IRIs, RDF,
SPARQL, typed Entio operations, source writes, or approval instructions.

## Bounds And Failure Behavior

- Logical calls and provider attempts are counted separately.
- A retryable failure receives at most one exact-input retry.
- Output-limit and provider-unavailable failures split groups deterministically
  while retaining successful group results.
- Grounded work is bounded to 15 logical calls and 20 provider attempts.
- Cancellation is checked before grouping and every provider attempt.
- Authorization, timeout, network, response-limit, and malformed structured
  output failures use existing safe provider codes.
- No orchestration path was switched in this slice.

## Verification

The following commands passed:

```text
./gradlew :web-server:test --tests '*DocumentGroundedAnalysisServiceTest*' --tests '*OpenAiDocumentAnalysisClientTest*'
./gradlew :web-server:compileKotlin
git diff --check
```

The focused run executed 32 tests. It covers exact-input retry, adaptive split,
complete coverage, invented selection rejection, cancellation before provider
access, strict no-tools request construction, secret exclusion, and structured
response parsing alongside the existing OpenAI failure-classification suite.

## Qualified-Meaning Correction

The provider instruction was tightened after end-to-end review exposed broader
ontology matches replacing qualified document concepts. The grounded model must
now preserve the specific candidate label and may not treat a shared head noun
or broader selected IRI as semantic equivalence. A stable narrower type is
modeled with its connected superclass relationship. If the evidence does not
safely decide between reuse and a narrower type, the qualified concept remains
`Unresolved` with the broader class retained as a model-recommended superclass.

Focused tests assert these provider-boundary requirements. No response contract,
provider authority, compiler behavior, or write boundary changed.

The correction was verified with:

```text
./gradlew :web-server:test --tests 'com.entio.web.ingestion.DocumentGroundedAnalysisServiceTest' --tests 'com.entio.web.ingestion.OpenAiDocumentAnalysisClientTest'
./gradlew :web-server:compileKotlin
git diff --check
```

All 37 focused tests passed. Stale generated test classes carrying a ` 2`
filename suffix were removed from `web-server/build/`; no source or tracked file
was deleted.

Git status: committed as one focused correction on
`fix/phase-12-qualified-grounding` and authorized for its remote branch only.
