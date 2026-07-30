# Phase 11.5+ Slice 7 Benchmark And Regression

## Decision

Phase 11.5+ uses a permanent, opt-in controlled-provider benchmark alongside
the offline regression suite. The default test suite does not make network
calls. A release-gate run requires an explicitly enabled provider credential
and exact model ID.

The benchmark keeps the two existing PDFs and all approved positive and
negative expectations. Its structured request clusters related meanings from
each document so the coverage ledger represents coherent discoveries rather
than treating every expected label as an unrelated discovery. Every supplied
connected-model item must still appear separately with its exact label and
supported semantic kind. The benchmark uses an explicit blocked coverage
disposition for each discovery because the controlled run has no human review
decision; this does not change compilation scoring or permit automatic apply.

One bounded correction call is available when either strict response parsing
or deterministic completeness verification rejects the initial response.
Provider failures are reported only through redacted categories.

## Controlled Run

- Date: 2026-07-30
- Model: `gpt-4o-mini`
- Contract: `phase-11-5-plus-semantic-plan-response-v1`
- Runs: 10
- Frozen input SHA-256:
  `6c41e84b1d1839dc2b4539753265ca41b1a83ab65fcaa2fdbc7c5eb1a9538b59`
- Credential source: local macOS Keychain; the credential was passed only
  through `OPENAI_API_KEY` for the benchmark process and was not written to the
  repository or command output.
- Command:

  ```bash
  OPENAI_API_KEY="<keychain value>" \
    ENTIO_DOCUMENT_BENCHMARK=true \
    ENTIO_DOCUMENT_BENCHMARK_MODEL=gpt-4o-mini \
    ./gradlew :web-server:test \
      --tests '*DocumentSemanticProviderBenchmarkTest*'
  ```

## Results

- Every required core concept appeared in 10 of 10 runs.
- Every required major relationship appeared in 10 of 10 runs.
- Every required complex review-only meaning appeared in 10 of 10 runs.
- Supported compilation success was 100%.
- Exact provenance passed in 10 of 10 runs.
- The complete coverage ledger passed in 10 of 10 runs.
- Illustrative-individual gates passed in 10 of 10 runs.
- Prohibited executable recommendations appeared in 0 runs.
- Automatic ontology writes occurred in 0 runs.
- Every run succeeded on its first provider attempt; no correction calls were
  needed.
- Run durations in milliseconds were:
  `36968`, `39524`, `92382`, `38364`, `41468`, `50171`, `82126`, `44948`,
  `46430`, and `63645`.
- Token usage and cost are unavailable because the current provider adapter
  does not expose them.

## Consequences

The benchmark demonstrates that the selected model can preserve the frozen
connected model, produce a complete review-bound ledger, and compile supported
meaning without emitting prohibited recommendations or writing the ontology.
It does not authorize approval, staging, apply, or any second write path.

Any future comparison must keep the PDFs, structured request, prompt, limits,
contract, scoring, and thresholds fixed for all compared runs. A changed input
or prompt requires a new frozen hash and an explicitly recorded result.
