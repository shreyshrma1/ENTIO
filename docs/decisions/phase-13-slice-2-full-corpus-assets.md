# Phase 13 Slice 2: Full-Corpus Descriptors And Foundation Assets

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-2-full-corpus-assets`

Corrective branch: `fix/phase-13-slice-2-semantic-label-fallback`

## Scope

Slice 2 adds deterministic text assets for the complete Phase 13 FIBO and OMG
Commons corpus. It uses only the pinned local Phase 5 package and Apache Jena;
generation performs no network access and adds no embedding model, vector
runtime, web route, UI, proposal, or reuse behavior.

The assets live under
`external-ontologies/domain-search/fibo/master_2026Q2/` and are independent of
the Phase 5 catalog files.

## Generated corpus

The generator traces every record to an eligible typed statement in its
recorded source file, rejects duplicate canonical IRIs, and produces canonical
IRI order. Generated counts are:

| Source family | Classes | Object properties | Datatype properties | Total |
| --- | ---: | ---: | ---: | ---: |
| FIBO | 3,021 | 948 | 263 | 4,232 |
| OMG Commons 1.3 | 148 | 166 | 33 | 347 |
| Total | 3,169 | 1,114 | 296 | 4,579 |

`sourceFamily` is derived only from the verified source path. Files beneath
`source/` are `FIBO`; files beneath
`dependencies/omg-commons-1.3/` are `OMG_COMMONS`; any other path fails
generation.

Each descriptor contains its kind, label, alternate labels, definitions,
named hierarchy, named domain and range, source ontology, source path,
descriptor text, source family, maturity, dependency fingerprint, record
fingerprint, and explicitly reported unsupported constructs. Descriptor text
is bounded to 64 KiB and named graph context to 128 distinct IRIs without
silent truncation.

Eligible records whose compact source record has no explicit label use the
existing `SemanticLabelPolicy` readable-IRI fallback. Generation and
verification reject blank preferred labels and blank descriptor text. This
correction guarantees that every one of the 4,579 records has deterministic
input for the Slice 3 embedding pipeline without adding structural IRIs to the
embedding text.

The corrective branch regenerated 126 formerly empty-label records and passed
the complete Slice 2 verification set shown below before commit and local
merge.

Maturity is read from the source ontology, with entity-level
`owl:deprecated true` taking precedence. The generated package contains 3,300
release, 1,161 provisional, 62 informative, and 56 deprecated records.

The unsupported-construct report contains 2,196 deterministic entries for
constructs outside the later materialization allowlist, including anonymous
structural expressions. Named supported context remains available separately;
the generator does not reinterpret or flatten those OWL expressions.

## Foundation review

The versioned foundation profile contains eight ordered groups and 42 reviewed
members:

1. agents and organizations — 6;
2. agreements and commitments — 5;
3. identifiers and classifications — 5;
4. dates and temporal concepts — 5;
5. quantities, units, and measures — 6;
6. ownership and control — 5;
7. products and services — 5;
8. places and addresses — 5.

Every group contains classes and at least one property. Every member resolves
to one generated descriptor and records its actual FIBO or OMG Commons source
family. The profile is Entio presentation metadata over the approved snapshot;
it does not claim to redefine FIBO foundations.

## Reproducibility and compatibility

`generateFiboCatalog` preserves its Phase 5 regeneration contract after first
verifying that the committed Phase 5 output is reproducible, then generates
the separate Phase 13 assets. `verifyFiboCatalog` verifies Phase 5 first,
checks the Phase 13 manifest and checksum ledger, rejects missing, duplicate,
corrupt, or stale records, regenerates into a temporary directory, and compares
all text assets byte for byte.

The Phase 5 compatibility hashes remain:

- manifest: `05e9c612bd308fec918ff3e4edc3b5bda422b23fad79bffae10e8ebce03373a5`;
- catalog metadata: `65ec3b1bf37bc703163c2bf82f1da2e4108b704acda22462ccf31c05af32acfc`;
- ordered catalog: `8194bc5cad5827aa98a2a6586c6a9a9da1cdf40c5f77681b0d56fa4e5868cb05`;
- curated foundations: `5d538592282548be0b021248e3c0a398e268a3a4d6c1de2627af81ee0f29da50`.

The Phase 12 `DocumentOntologyRetrievalService.kt` fingerprint remains
`dc7089f4618e390db4b7d0b3d4c0ba17d5376ef0d00246e3a652ad62eaba0f90`.

## Verification

The required commands passed on 2026-08-08:

```text
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:test --tests '*Fibo*'
./gradlew :semantic-engine:test --tests '*DomainFoundation*'
git diff --check
```

Focused tests cover full traversal and identity deduplication, exact kind and
source-family counts, representative label/definition/hierarchy/domain/range
metadata, all maturity states, unsupported-construct reporting, foundation
membership and order, deterministic regeneration, checksum corruption, stale
package fingerprints, and unchanged Phase 5 and Phase 12 compatibility hashes.

## Deferred work

Slice 3 adds only the approved local embedding dependencies, pinned model
assets, vectors, and BM25 index. Retrieval ranking, recommendation state,
activation routes, materialization, and UI remain deferred to their later
slices.
