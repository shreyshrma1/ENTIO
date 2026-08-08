# Phase 13 Slice 3: Local Embeddings And Hybrid Index

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-3-local-hybrid-index`

## Scope

Slice 3 adds the approved local embedding runtime, pinned model assets,
deterministic lexical documents, and canonical-IRI-ordered exact-scan vectors.
It does not add recommendation policy, semantic eligibility, web routes, UI,
proposal behavior, a hosted model, a Python production runtime, or a vector
database.

## Local model contract

The implementation uses `sentence-transformers/all-MiniLM-L6-v2` revision
`94ea1512acaefbfe2e255b2d2ea4bf0d9d7b3dc3` through ONNX Runtime CPU 1.28.0
and DJL Hugging Face tokenizers 0.36.0. It verifies the model and tokenizer
checksums before opening either resource. Input is bounded to 64 KiB and 256
wordpieces, special tokens are enabled, dynamic padding is disabled, and the
384-dimensional result uses attention-mask mean pooling followed by L2
normalization.

The committed model directory includes the Apache-2.0 license and an Entio
NOTICE that identifies Sentence Transformers, DJL/Hugging Face tokenizers,
ONNX Runtime, and Lucene. Inference is local and has no download or hosted
fallback.

## Search assets

The offline generator produced:

- 4,579 canonical lexical documents for Lucene 10.5.0 BM25 loading;
- 4,579 canonical IRIs in ascending order;
- 4,579 normalized float32 vectors with 384 dimensions;
- a 7,033,344-byte little-endian vector file;
- `entio-domain-search-index-v1` manifest and checksum ledger.

The manifest binds the pinned FIBO package, corrected descriptor fingerprint,
record and IRI order, lexical contract, model revision and hashes, text and
pooling contracts, vector shape and encoding, graph-context contract, and the
future Slice 4 ranking contract. The model plus generated corpus occupies
about 110 MiB, below both the projected 180 MiB bundle and the 250 MiB stop
gate.

Lucene indexes the committed deterministic lexical documents into an embedded
RAM directory at load time. This preserves reproducible committed inputs while
using Lucene BM25 mechanics. Full mode additionally verifies and loads the
exact-scan vectors. Lexical-only mode does not read the vector artifact and
continues to work if that artifact is unavailable or corrupt.

## Verification

The required commands passed on 2026-08-08:

```text
./gradlew :semantic-engine:generateDomainSearchIndex
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew :semantic-engine:test --tests '*LocalSentenceEmbedding*'
./gradlew :semantic-engine:test --tests '*DomainSearchIndex*'
./gradlew :semantic-engine:build
git diff --check
```

Tests cover the Python reference-vector tolerance, token truncation, blank and
oversized input, deterministic and concurrent inference, resource closure,
model checksums, exact entity/dimension/byte counts, normalization, lexical and
vector identity joins, stable exact-scan ordering, corruption and wrong-model
rejection, lexical-only operation, concurrent BM25 queries, deterministic
offline regeneration, and the approved interactive exact-scan bound.

## Deferred work

Slice 4 owns candidate union, semantic eligibility, structural reranking,
explanations, confidence, recommendation identity, and degraded-mode policy.
No vector value is treated as ontology truth.
