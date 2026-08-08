# Phase 13 Local Hybrid Retrieval

Date: 2026-08-08

Status: Approved by the Phase 13 Slice 0 audit

## Context

Phase 13 needs full-corpus FIBO recommendations rather than searches over the
curated Phase 5 subset. Search must remain local, deterministic for frozen
inputs, small enough to distribute, and fast on the supported Java 21
development machine.

## Approved source corpus

`master_2026Q2` is an Entio-approved snapshot of FIBO master at commit
`f59157fe156e3d91b1c045222d0a7dc06b7d78a2`; it is not described as an official
FIBO production publication. Its package fingerprint is
`015142b94819291379b89c3bba92048f037f1d8e635d3f1342d29f0f02f374ad`.
The embedded archive contains the same `297` RDF/Turtle source files recorded
by the package manifest.

The `4,579` eligible records are:

| Source family | Classes | Object properties | Datatype properties | Total |
| --- | ---: | ---: | ---: | ---: |
| FIBO | 3,021 | 948 | 263 | 4,232 |
| OMG Commons 1.3 | 148 | 166 | 33 | 347 |

`sourceFamily` is `OMG_COMMONS` only when the verified `sourcePath` starts with
`dependencies/omg-commons-1.3/`; all verified `source/` records are `FIBO`.
Any other path is invalid. Duplicate eligible canonical IRIs are rejected.
Default recommendations search FIBO records and include Commons only when a
FIBO structural dependency or active project context reaches them. Explicit
broad search may include Commons and must show its source-family badge.

## Dependencies and model

- Lucene Core `10.5.0` and Lucene Analysis Common `10.5.0`, used only for local
  BM25 mechanics;
- ONNX Runtime CPU `1.28.0`;
- DJL Hugging Face tokenizers `0.36.0` and DJL API `0.36.0`;
- `sentence-transformers/all-MiniLM-L6-v2`, revision
  `94ea1512acaefbfe2e255b2d2ea4bf0d9d7b3dc3`;
- ONNX artifact `onnx/model.onnx`, SHA-256
  `6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452`;
- `tokenizer.json`, SHA-256
  `be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037`;
- `tokenizer_config.json`, SHA-256
  `acb92769e8195aabd29b7b2137a9e6d6e25c476a4f15aa4355c233426c61576b`;
- `special_tokens_map.json`, SHA-256
  `303df45a03609e4ead04bc3dc1536d0ab19b5358db685b6f3da123d05ec200e3`;
- `config.json`, SHA-256
  `953f9c0d463486b10a6871cc2fd59f223b2c70184f49815e7efbcab5d8908b41`.

The model and tokenizer are Apache-2.0. Distribution must include their
license, model attribution, revision, checksums, and a NOTICE identifying
Sentence Transformers, Hugging Face tokenizers/DJL, ONNX Runtime, and Lucene.
ONNX Runtime is MIT; Lucene and DJL are Apache-2.0. Slice 0 found no OSV entries
for the selected Maven coordinates on 2026-08-08.

The model contract is UTF-8 text, uncased WordPiece tokenizer from the pinned
`tokenizer.json`, special tokens enabled, dynamic padding disabled, truncation
at `256` wordpieces, mean pooling over tokens whose attention mask is `1`, L2
normalization, and `384` float dimensions. `domain-embedding-text-v1` joins the
preferred label, ordered alternate labels, and ordered definitions with
`. ` after dropping blank values. Structural IRIs and project text are not
added to corpus embedding text; they remain separate ranking features.

The selected unquantized artifact is architecture-neutral. Phase 13 full-mode
performance acceptance is supported on macOS ARM64/Apple Silicon with Temurin
Java 21. Linux x64/ARM64 and Windows x64 remain functional targets when their
bundled natives pass Slice 3 smoke tests; other platforms use explicit
lexical-structural degraded mode. Intel macOS is not a Phase 13 full-mode
target because the selected runtime jars do not bundle the required natives.

## Candidate comparison and proof

The measured alternative was `all-MiniLM-L12-v2` revision
`3a71443a25d6524212d4f2d995c533eb3f6e56ca`, also Apache-2.0 and 384-dimensional.
On the Apple M2 baseline, quantized L6 averaged `2.370 ms` for the probe query
and L12 `4.474 ms`; corpus generation took `52.506 s` and `103.105 s`
respectively. Across the development and regression query sets, both averaged
the same recall@10 (`0.925`), while L6 had higher combined MRR and was smaller.
L6 is therefore selected. Locked-set vector-only results are diagnostic and
were not used to tune the fixed hybrid weights or confidence thresholds; the
acceptance gate applies to the completed hybrid service in Slice 4.

Temurin `21.0.12+8` on Apple M2 produced the same normalized 384-dimensional
vector as a Python ONNX Runtime/tokenizers reference for
`a financial contract between a lender and borrower`: maximum absolute error
`1.5e-8`, cosine approximately `1.0000000012`. The Java vector text SHA-256 was
`eab395fcd5d19a3af659aee4f8d8b7e49f9c1463f6b89c8f106ca88e14ee5f69`.

## Vector layout and scan

Vectors are written in ascending canonical-IRI UTF-8 byte order. The manifest
contains record count, dimension, float encoding, byte order, model and text
contract versions, ordered-IRI fingerprint, vector-file fingerprint, and every
source/index fingerprint. Values are IEEE-754 float32 in little-endian order.
Each stored and query vector must be finite and L2-normalized within `1e-5`.

Exact cosine scan is a dot product over every record in manifest order. It
keeps the best 100 by score descending, then canonical IRI ascending for exact
score ties. NaN, infinity, wrong dimension, wrong count, or a zero vector makes
the vector index unavailable. A Java 21 primitive-array scan of 4,579 × 384
float values used `7,033,344` bytes and measured `2.360 ms` p95 and `2.578 ms`
p99 over 2,000 measured scans after warm-up.

## Packaging and benchmark ownership

Phase 13 assets live only below
`external-ontologies/domain-search/`. They do not modify Phase 5 package files,
manifest, catalog, metadata, or ordering. The model, tokenizer, licenses,
descriptors, Lucene index, vectors, and manifests are committed, checksum-
verified assets. The measured model is about 86 MiB; the projected complete
incremental bundle is below 180 MiB and the 250 MiB gate.

The semantic-engine maintainers own benchmark mechanics; the product owner
owns relevance approval. Benchmark v1 contains separate development,
regression, and locked sets. Each case records allowed kinds, all relevant
IRIs, hard negatives, no-match status, and rationale. The repository owner
approved the 15 locked judgments as reviewer 2 on 2026-08-08; the implementation
reviewer supplied reviewer 1 judgments. Locked cases may be executed but not
used to change ranking weights or confidence thresholds. Any judgment change
requires a version bump and audit note.

Precision@3 is macro-averaged as relevant actionable results divided by the
number of actionable results returned in the first three positions; an
abstention/no-match result is evaluated by the separate no-match metric.
Recall@10 is macro-averaged over positive cases. Confidence bands are calibrated
on the development set, checked on regression, and applied unchanged to the
locked set. Phase 5 baselines are its existing exact IRI, `agreement`,
`borrower`, `situation`, object-property compatibility, and repeated-ordering
tests; lexical degraded mode must preserve those outcomes.

## Bounded in-memory state

Recommendation records and frozen plans expire 30 minutes after creation.
Each `(projectId, userId)` scope holds at most 500 recommendation records and
50 frozen plans. Access does not refresh age. Cleanup removes expired records
first, then the smallest server-issued monotonic sequence until within
capacity. Associated plans/records are removed together. Restart invalidates
all tokens. Cleanup runs on write and bounded read, and no raw query or vector
is logged.

## Consequences

- Interactive embedding never sends text outside the process.
- Exact scan is simpler and materially below the latency budget.
- Missing or invalid native/model/vector assets degrade explicitly to lexical
  and structural search.
- Phase 5 and Phase 12 retrieval remain compatibility boundaries rather than
  being silently replaced.
