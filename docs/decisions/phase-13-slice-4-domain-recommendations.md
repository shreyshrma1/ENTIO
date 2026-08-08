# Phase 13 Slice 4: Kotlin Domain Recommendations

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-4-domain-recommendations`

## Scope

Slice 4 adds the Kotlin-owned recommendation boundary over the complete Phase
13 domain corpus and local hybrid index. It defines bounded modeling intents,
combines lexical and vector candidates, applies semantic eligibility before
ranking, returns structured reasons and permitted actions, and retains only
bounded temporary recommendation state. It adds no Ktor routes, UI behavior,
proposal writes, assistant integration, or document-ingestion integration.

## Recommendation contract

The implementation provides:

- fixed operation, availability, confidence, reason, warning, and action
  contracts;
- a canonical union of the top 100 lexical and top 100 vector candidates;
- hard source-family, entity-kind, domain, range, maturity, and freshness
  eligibility;
- the approved `domain-ranking-v1` weights: 35 percent lexical, 25 percent
  vector, 20 percent structure, 15 percent project context, and 5 percent
  foundation context;
- deterministic score ordering and canonical-IRI tie-breaking;
- structured reasons derived only from verified record and intent features;
- stable recommendation identifiers bound to user, project, intent, source,
  index, and ranking fingerprints;
- default 10-result and explicit broad 50-result bounds;
- full local-hybrid and explicit lexical-structural degraded modes; and
- a non-refreshing 30-minute TTL with 500 recommendation and 50 plan records
  per project/user scope, expired-first then oldest-sequence eviction, and
  restart invalidation.

Canonical IRIs remain external identity. The service does not stage, apply, or
write ontology changes and does not expose vectors as semantic facts.

## Benchmark history

Benchmark v1 remained unchanged at SHA-256
`d47230480bb458b1d65e0fdd4326d82f7b880f0497493f1f512386e32f25dab1`
through every run. Ranking weights, confidence thresholds, candidate limits,
judgments, relevant IRIs, hard negatives, and metric definitions were not
changed after the locked set was opened.

The first locked run stopped on recall@10 `0.8333333333`; precision@3 was
`0.75`, and every other gate passed. The approved single-token normalization
correction fixed the focused `Bond` ordering but unintentionally narrowed a
pre-existing multi-token acronym signal. The first conformance rerun therefore
still produced recall@10 `0.8333333333`; precision@3 was `0.7272727273`, and
every other gate passed. Both failures and their approvals are preserved in
`docs/decisions/phase-13-locked-benchmark-v1-first-run.md`.

The final approved correction retained exact acronym matching for one-token
queries and restored token-based acronym recognition only for multi-token
queries. Development and regression passed before the final locked run.

| Set | Recall@10 | Precision@3 | Kind correctness | Hard-negative suppression | No-match correctness | Stable ordering |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Development | `1.00` | `0.80` | `1.00` | `0.8888888889` | `1.00` | Yes |
| Regression | `1.00` | `0.9166666667` | `1.00` | `1.00` | `1.00` | Yes |
| Locked | `0.9166666667` | `0.7727272727` | `1.00` | `1.00` | `1.00` | Yes |

The locked set exceeds its recall@10 `0.85` and precision@3 `0.70` gates and
meets every exact correctness and ordering gate. The Phase 5 comparison remains
compatible: exact-label, borrower, agreement, entity-kind, compatibility, and
repeated-ordering behaviors are retained, while Phase 13 evaluates the full
4,579-entity corpus and adds paraphrase and no-match coverage.

## Verification

The required commands passed on 2026-08-08:

```text
./gradlew :semantic-engine:test --tests '*DomainRecommendation*'
./gradlew :semantic-engine:test --tests '*DomainRetrievalBenchmark*'
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew :semantic-engine:build
git diff --check
```

Focused conformance tests also passed for exact single-token base-label
ordering and multi-token acronym recognition. Performance measurements remain
separate from unit-test pass/fail timing and use the approved Slice 0 and Slice
3 baseline records.

## Deferred work

Slice 5 owns authorized Ktor read contracts, profile activation/deactivation,
request bounds, and route-level state invalidation. Slice 6 owns controlled
reuse materialization and atomic provenance. Later approved slices integrate
the recommendation service into the existing user workflows.
