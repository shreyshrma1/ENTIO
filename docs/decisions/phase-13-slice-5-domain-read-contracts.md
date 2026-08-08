# Phase 13 Slice 5: Domain Profile And Recommendation Web Contracts

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-5-domain-read-contracts`

## Scope

Slice 5 exposes the approved Phase 13 domain-ontology profile, foundation,
recommendation, dependency-preview, and migration reads through the versioned
Ktor boundary. It also exposes previewed, confirmed activation and deactivation
of the fixed FIBO profile. Kotlin semantic services continue to own profile
meaning, dependency closure, recommendation ranking, freshness, and recovery.

This slice adds no React or VS Code behavior, ontology reuse staging, assistant
or document-ingestion integration, raw vectors, client-selected package paths,
or second search implementation.

## Delivered contracts

The web server now provides:

- the fixed FIBO domain-ontology catalog and project profile status;
- activation and deactivation previews followed by token-bound, idempotent
  confirmation routes;
- the eight reviewed foundation groups with opaque server-issued element IDs;
- deterministic foundation plans with at most 20 explicit selections and 100
  closure entities per batch;
- project- and user-scoped recommendation and plan resolution;
- structured recommendation details and dependency previews;
- read-only recognition and preview of existing FIBO reuse for migration; and
- versioned structured errors that do not reveal filesystem or model paths.

The routes are:

```text
GET  /api/v1/domain-ontologies
GET  /api/v1/projects/{projectId}/domain-ontology
POST /api/v1/projects/{projectId}/domain-ontology/activation-preview
POST /api/v1/projects/{projectId}/domain-ontology/activate
POST /api/v1/projects/{projectId}/domain-ontology/deactivation-preview
POST /api/v1/projects/{projectId}/domain-ontology/deactivate
GET  /api/v1/projects/{projectId}/domain-ontology/foundation
POST /api/v1/projects/{projectId}/domain-ontology/foundation-plans
GET  /api/v1/projects/{projectId}/domain-ontology/foundation-plans/{planId}
POST /api/v1/projects/{projectId}/domain-recommendations
GET  /api/v1/projects/{projectId}/domain-recommendations/{recommendationId}
POST /api/v1/projects/{projectId}/domain-recommendations/{recommendationId}/dependency-preview
GET  /api/v1/projects/{projectId}/domain-migration
POST /api/v1/projects/{projectId}/domain-migration/preview
```

The recommendation stage route and domain-reuse detail route remain assigned
to Slice 6, where managed materialization and provenance are implemented.

## Activation and state safety

Activation verifies the pinned FIBO package and local search index before
preparing changes. It creates only the fixed profile sidecar and empty managed
Turtle source, leaves `entio.yaml` byte-for-byte unchanged, commits the profile
as the observable transaction point, reloads the project for semantic
verification, and invalidates the loaded-project cache. Normal project loading
retains startup transaction recovery; the transaction commit callback uses a
non-recovering loader only to verify the prepared state while its journal is
necessarily still present.

Deactivation uses the same transaction manager and is permitted only when the
managed source is empty and no staged work, proposal, or retained provenance
depends on the profile. Confirmation tokens are bound to action, user, project,
configuration fingerprint, and a ten-minute lifetime. Successful action
responses are retained in a bounded 100-entry, 24-hour in-process idempotency
window, while conflicting payloads fail closed.

Recommendation text is bounded to 2,000 decoded characters. Semantic context
IRIs and source IDs are resolved against the authorized loaded project before
the server constructs the authoritative modeling intent. Recommendation and
foundation-plan state remains bounded, fingerprinted, expiring, and
restart-invalidated in the semantic engine. Ktor permits at most eight active
recommendation executions and runs each in an interruptible ten-second window,
so request cancellation and timeout propagate without mutating project state.
Ktor does not calculate ranks, scores, or dependency closure.

## Verification

The required commands passed on 2026-08-08:

```text
./gradlew :web-server:test --tests '*DomainOntology*'
./gradlew :web-server:test --tests '*DomainRecommendation*'
./gradlew :web-server:test --tests '*WebContract*'
./gradlew :web-server:build
git diff --check
```

Focused semantic recommendation tests also passed for deterministic bounded
foundation planning and project/user ownership. Existing transaction-manager
tests continue to cover prepare, commit, rollback, and recovery steps. A stale
duplicate class in generated `web-server/build` output was removed with the
standard module clean task; a clean build and the subsequent exact build
command both passed, and no duplicate source file existed.

## Deferred work

Slice 6 owns managed reuse, customization, mapping, provenance, proposal
staging, the recommendation stage route, and domain-reuse details. Later
approved slices integrate these contracts into the web and VS Code clients and
the remaining human-driven ontology workflows.

## Package-root correction

Slice 6 preflight found that the web asset locator had appended the release
name to the already versioned Phase 5 package layout. The fixed package root is
`external-ontologies/fibo`; its manifest owns the pinned release identity. The
locator and focused asset-discovery test were corrected before Slice 6
implementation continued. Search and model roots were already correct.
