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
