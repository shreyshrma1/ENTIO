# Phase 13 Summary

## Status

Phase 13 was implemented and verified on 2026-08-11.

Phase 13 changes FIBO from an always-present browser catalog into an explicit,
project-scoped domain choice. After activation, users can inspect foundational
entities, search the verified full corpus, selectively reuse canonical FIBO
identities, and receive domain recommendations throughout applicable human-
driven modeling workflows. Kotlin remains authoritative for identity,
retrieval, dependency closure, freshness, provenance, validation, and writes.

## Delivered Behavior

- Optional transactional activation of the pinned FIBO `master_2026Q2`
  package, with explicit preview and no silent default selection.
- A verified 297-source-file, 4,579-entity full-corpus descriptor package and a
  local hybrid index combining lexical, structural, and 384-dimensional vector
  evidence.
- Deterministic full, lexical/structural degradation, and unavailable modes;
  there is no runtime network retrieval or vector-database service.
- Foundation browsing and selective reuse into a project-owned managed source.
- Canonical FIBO IRIs remain fixed while supported project-owned labels,
  definitions, and modeling statements can be edited through normal review.
- Immutable applied provenance outside ontology source files, including
  release, package, descriptor, dependency, actor, proposal, and apply identity.
- Server-issued, project/user-bound recommendations with freshness,
  authorization, kind, dependency, and duplicate verification before staging.
- Domain recommendations in global search, contextual class/property/
  individual creation, annotation editing, mapping, SHACL, reasoning,
  document-review handoff, and proposal review surfaces.
- Compatible CLI and VS Code read adapters without adding a second semantic
  engine or write path.
- Detection and non-mutating migration previews for projects that already use
  FIBO identities, without guessing historical releases or provenance.

Document ingestion and the native ontology assistant intentionally retain their
Phase 12 retrieval contracts. All ontology changes still use the existing
private-draft, proposal, validation, human approval, atomic apply, reload, and
rollback workflow.

## Delivery Records

| Slice | Branch | Tip commit | Completion artifact |
| ---: | --- | --- | --- |
| 0 | `feature/phase-13-slice-0-contract-audit` | `53fc404` | `docs/decisions/phase-13-slice-0-contract-audit.md` |
| 1 | `feature/phase-13-slice-1-domain-profile` | `91207b1` | `docs/decisions/phase-13-slice-1-domain-profile-and-transactions.md` |
| 2 | `feature/phase-13-slice-2-full-corpus-assets` | `f6932fd` | `docs/decisions/phase-13-slice-2-full-corpus-assets.md` |
| 3 | `feature/phase-13-slice-3-local-hybrid-index` | `3076e9b` | `docs/decisions/phase-13-slice-3-local-hybrid-index.md` |
| 4 | `feature/phase-13-slice-4-domain-recommendations` | `e8bc648` | `docs/decisions/phase-13-slice-4-domain-recommendations.md` |
| 5 | `feature/phase-13-slice-5-domain-read-contracts` | `7fae630` | `docs/decisions/phase-13-slice-5-domain-read-contracts.md` |
| 6 | `feature/phase-13-slice-6-managed-domain-reuse` | `fb11a48` | `docs/decisions/phase-13-slice-6-managed-domain-reuse.md` |
| 7 | `feature/phase-13-slice-7-domain-web-experience` | `d2c8048` | `docs/decisions/phase-13-slice-7-domain-web-experience.md` |
| 8 | `feature/phase-13-slice-8-contextual-authoring` | `0a8387a` | `docs/decisions/phase-13-slice-8-contextual-authoring.md` |
| 9 | `feature/phase-13-slice-9-cross-workflow-integrations` | `35312d7` | `docs/decisions/phase-13-slice-9-cross-workflow-integrations.md` |
| 10 | `feature/phase-13-slice-10-client-compatibility` | `6af2be0` | `docs/decisions/phase-13-slice-10-client-compatibility.md` |
| 11 | `feature/phase-13-slice-11-existing-project-migration` | `94c7dbe` | `docs/decisions/phase-13-slice-11-existing-project-migration.md` |
| 12 | `feature/phase-13-slice-12-quality-and-documentation` | this completion commit | `docs/decisions/phase-13-slice-12-quality-security-performance.md` |

Every slice was implemented on its own branch, tested, committed once, pushed
without force, and merged locally into `main` using a non-fast-forward merge.
`main` was not pushed. Focused corrective and approved conformance branches are
retained separately in Git history.

## Quality And Performance

The unchanged locked retrieval benchmark passed with recall@10 0.9167,
precision@3 0.7727, kind correctness 1.0, hard-negative suppression 1.0,
no-match correctness 1.0, and stable ordering. Development and regression
fixture gates also passed.

The isolated benchmark ran on an Apple M2 with 16 GiB memory, macOS 26.5.2
arm64, and Temurin 21.0.12+8. The table records conservative maxima across five
passing fresh-process runs:

| Measure | Result | Approved limit |
| --- | ---: | ---: |
| Warm index load | 706 ms | 3,000 ms |
| First local inference | 4,785 ms | 5,000 ms |
| Warm default p95 | 51 ms | 300 ms |
| Lexical/structural p95 | 38 ms | 150 ms |
| Broad-search p95 | 46 ms | 750 ms |
| Eight-request concurrent p95 | 158 ms | 1,000 ms |
| Model plus index assets | 106,563,771 bytes | 250 MiB |
| Peak benchmark memory footprint | 98,173,816 bytes | 512 MiB |

Catalog generation took 14 seconds, search-index generation 54 seconds, catalog
verification 10 seconds, and independent search-index regeneration and
verification 53 seconds. All stayed below the approved 15-minute limit.

## Security, Privacy, And Failure Behavior

Checksum mismatch, corrupt model/index/package data, unsafe project paths and
symlinks, stale or cross-scope IDs, oversized inputs, and mismatched fingerprints
fail closed. Corrupt vectors may degrade only to verified lexical/structural
search. Read routes do not write ontology or profile state, provenance is
committed atomically with ontology application, and injected failures restore
the prior files and records. Browser content is rendered as text, errors and
logs exclude sensitive payloads, raw vectors are not exposed, and retrieval has
no network fallback.

## Verification

The accumulated implementation passed:

```text
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:generateDomainSearchIndex
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew :semantic-engine:phase13PerformanceBenchmark
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

The production web dependency audit found zero vulnerabilities. The final web
suite passed 119 tests and four Playwright workflows; the VS Code extension
passed 39 tests. Gradle test, build, and check all passed. The generated catalog,
model, and search index licenses, manifests, fingerprints, and checksums were
verified from repository assets.

## Non-Goals Preserved

Phase 13 adds no automatic approval, direct RDF generation, second apply path,
runtime ontology download, external retrieval service, vector database,
production persistence, new identity model, unrestricted agent loop, new
domain ontology beyond FIBO, document-ingestion RAG expansion, or ontology-
assistant RAG expansion.
