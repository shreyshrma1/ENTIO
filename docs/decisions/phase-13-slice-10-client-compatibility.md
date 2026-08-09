# Phase 13 Slice 10: Client And Compatibility Migration

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-10-client-compatibility`

## Scope

Slice 10 gives machine clients explicit Phase 13 profile and reuse reads while
preserving the Phase 5 and Phase 12 compatibility boundaries. The CLI remains
a thin adapter over Kotlin services. VS Code presents Kotlin results and does
not rank candidates, resolve dependencies, or mutate a domain profile.

No document-ingestion or assistant production file changed in this slice.
Document ingestion continues to use `DocumentOntologyRetrievalService` and the
pinned `FiboCatalogLoader` adapter. The assistant continues to use its bounded
`FiboWebService` context path, with at most eight search terms and twenty
distinct returned entries. Neither flow receives the Phase 13 embedding model,
search index, recommendation service, profile tools, or recommendation IDs.

## Machine-readable CLI contracts

The CLI now exposes read-only commands for:

- `domain-sources`;
- `domain-profile-status`;
- `domain-activation-preview`;
- `domain-foundation`;
- `domain-foundation-plan`;
- `domain-recommendations` and its `domain-search` alias;
- `domain-describe`;
- `domain-dependencies`;
- `domain-proposal` and its `domain-proposal-preview` alias.

Profile activation apply remains server-owned. Recommendation search delegates
to `DomainRecommendationService`; description and reuse previews delegate to
`DomainReuseService`. The proposal command prepares a bounded, read-only batch
and never stages or writes it. Existing `external-*` CLI commands remain
registered and unchanged for Phase 13 compatibility.

## VS Code profile awareness

The existing external workbench first reads `domain-profile-status`. An
inactive project says that no domain ontology is selected and does not load or
display FIBO as active. An active project browses all pinned modules and uses
`domain-recommendations` for full-corpus search. TypeScript only adapts the
server-ranked response for display; it does not invent scores or reorder it.

Existing detail and proposal paths remain Kotlin-owned. Where supported, a
selected entity also shows its source statement count, project statement count,
and source-versus-project classification.

## Legacy adapter lifecycle

All `/api/v1/projects/{projectId}/external/fibo/*` routes remain functional and
bounded for the complete Phase 13 lifecycle. Responses now carry `Deprecation:
true` and a warning that identifies them as retained compatibility routes.
Unknown projects return a structured `404`, and invalid pagination remains a
structured bounded-request error.

Removal is explicitly outside Phase 13. A future phase may remove these routes
and the `external-*` CLI commands only after document ingestion, the assistant,
VS Code, and any supported older client have migrated to replacement contracts
with byte/semantic compatibility tests passing. Until then, the adapters and
the old FIBO catalog assets are required runtime compatibility surfaces.

## Compatibility evidence

The frozen Phase 12 benchmark still matches the approved document hashes,
ontology hash, historical manifest hash, empty current-work fingerprint,
ordered candidate/retrieval serialization hash, prompt version, and response
version. The deterministic retrieval suite still returns identical ordered
choices for repeated inputs and preserves opaque selection IDs and authorized
scope order.

Architecture tests additionally verify that document and assistant production
packages do not reference `DomainRecommendationService`, `DomainSearchIndex`,
or `LocalSentenceEmbeddingService`. Existing AI workflow tests continue to
exercise the bounded assistant context and review-only proposal behavior.

## Offline reasoning compatibility

During verification, the historical CLI reasoning regression exposed an
existing network dependency: serializing an already-resolved project graph
back into OWLAPI caused `owl:imports` statements to be downloaded again. The
OMG endpoint returned HTTP `522`. With explicit user authorization for this
narrow semantic-engine exception, in-memory graph reasoning now omits only
`owl:imports` metadata from its temporary serialization. Authorized import
content is already present in the supplied resolved graph, while import
completeness remains represented by the existing `ImportClosureReport`.

A focused regression proves that graph reasoning derives expected consequences
without contacting an unreachable import IRI. The existing Phase 4 CLI
reasoning regression then passes offline.

## Verification

The required Slice 10 commands passed on 2026-08-08:

```text
./gradlew :cli:test
./gradlew :web-server:test --tests '*Document*'
./gradlew :web-server:test --tests '*Ai*'
(cd vscode-extension && npm test)
git diff --check
```

Additional focused checks passed for the frozen Phase 12 grounded benchmark,
the deterministic document retrieval service, active/inactive CLI contracts,
full-corpus VS Code response adaptation, source/project status, legacy-route
deprecation headers, unknown-project authorization, and pagination bounds.
