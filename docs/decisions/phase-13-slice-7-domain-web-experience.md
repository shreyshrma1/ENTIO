# Phase 13 Slice 7: Domain Web Experience

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-7-domain-web-experience`

## Scope

Slice 7 replaces the always-present FIBO browser with an optional domain
ontology workspace in the React application. It presents None/FIBO settings,
the exact pinned release and retrieval mode, explicit activation and
deactivation previews, foundation selection and stable server-issued batches,
inactive read-only catalog browsing, and active full-corpus search.

The UI does not rank recommendations, calculate dependencies, generate RDF, or
write project files. Activation, deactivation, foundation planning,
recommendation ranking, source verification, source-versus-project comparison,
and staging remain server-owned operations.

## Domain settings and foundation

Inactive projects show FIBO as an optional choice and retain bounded,
read-only catalog browsing. Selecting FIBO first displays the exact profile and
managed-source paths, serialized profile content, and whether ontology
statements change. Activation remains disabled until the user confirms that
preview. A failed activation reports that the prior project state was restored.

Active projects show reviewed foundation groups with group and member
selection, a select-all planning action, dependency counts, deterministic batch
contents, and explicit batch progress. Planning failures preserve the visible
selection so the user can revise or retry it. Deactivation uses the server
preview and displays blockers instead of offering an unsafe action.

## Unified search and details

The Domain workspace searches project concepts and the verified full FIBO
corpus together while keeping their result sets and provenance explicit.
Project hits carry local, imported, or project-reuse labels. Server-ranked
domain hits distinguish FIBO from OMG Commons and label available,
already-reused, and customized results without treating catalog availability as
application to the project.

Explore's existing debounced label search uses the same server contract when
the project profile is active. It keeps project hits actionable, adds explicit
local/imported/project-reuse labels, and presents matching FIBO or OMG Commons
results as links to the Domain review workspace. Inactive projects make no
domain recommendation request, and status or retrieval failure never removes
the local results.

Each submitted query has its own React Query key. A response for an older query
therefore cannot replace a newer result. The client presents the server-issued
retrieval mode, recommendation reasons, permitted actions, source snapshot,
current project statements, omitted source axioms, and Kotlin-calculated
differences. It does not infer compatibility itself. Partial materialization
requires explicit acknowledgement, and unavailable verified retrieval disables
new reuse staging while leaving ordinary local project work available.

The recommendation detail web response now includes the existing
`DomainReuseDifference` calculated by Kotlin. This narrow Ktor DTO correction
is required so React can present source and project meaning separately rather
than deriving semantic differences in the browser.

## Accessibility and compatibility

The workspace uses labeled controls, native checkboxes and selects, status and
alert regions, keyboard-focusable confirmations, explicit loading and empty
states, and stale-result guidance. The navigation label is now `Domain`, while
the internal `module=fibo` URL remains unchanged for saved-link compatibility.
The legacy bounded FIBO routes remain available for inactive manual browsing.

Document ingestion and the ontology assistant are unchanged, and contextual
authoring integrations remain deferred to the approved later slices.

## Verification

The required commands passed on 2026-08-08:

```text
(cd web-app && npm test)
(cd web-app && npm run build)
(cd web-app && npm run test:e2e)
./gradlew :web-server:test --tests '*DomainOntology*'
git diff --check
```

The ExecPlan's `npm test -- --runInBand` form was replaced with the repository's
supported `npm test` command as explicitly permitted by the plan. Results were
23 Vitest files and 106 tests passed, four Playwright journeys passed, the
production TypeScript/Vite build passed, and all focused domain-ontology server
tests passed. The server suite was rerun after cleaning stale generated test
classes left by a removed duplicate source file; the clean run passed.
The approved semantic-search E2E snapshot was updated solely for the new
visible `Local` source label.

Focused coverage includes inactive and active rendering, exact release and
activation preview, failure restoration, keyboard focus, foundation selection
and batches, unified local/imported/reused/customized/available results, FIBO
and OMG Commons identity, source/project differences, degraded and unavailable
states, and late-response suppression.
