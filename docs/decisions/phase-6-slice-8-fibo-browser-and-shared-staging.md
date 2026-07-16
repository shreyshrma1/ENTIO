# Phase 6 Slice 8: FIBO Browser And Shared Staging

## ExecPlan Slice

Slice 8 of `docs/execplans/0009-phase-6-collaborative-web-workbench-native-ai-foundation-execplan.md`.

## Goal

Expose the pinned, immutable FIBO catalog through the web workbench and route validated external ontology reuse or local-subclass intents into the existing shared staged-change workflow.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/Phase25PlusContracts.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/StagedChangeSetNormalizer.kt`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/FiboWebService.kt`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/test/kotlin/com/entio/web/ApplicationTest.kt`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/workbench/ExternalOntologyPanel.tsx`
- `web-app/src/workbench/ProjectWorkspace.tsx`
- `web-app/src/styles.css`

## Implemented Behavior

- Added paged curated-module, module-element, search, and details HTTP adapters over the existing FIBO catalog session.
- Added label-first external element presentation with definitions, alternate labels, original IRIs, and reviewed dependency information.
- Added web UI module browsing, scrollable element lists, search, details, dependency selection, and proposal intent selection.
- Added reuse-class, reuse-object-property, reuse-datatype-property, and local-subclass proposal staging through the common in-memory staged set.
- Preserved the external package boundary: FIBO assets remain read-only and external IRIs are not rewritten.
- Added `GraphChanges` as a narrow staged-operation representation for already translated external proposals.
- Made the default FIBO package path work when the server is launched from either the repository root or the `web-server` module directory.

## Tests And Verification

Added web-server coverage for paged modules, module elements, external search, labelled/details metadata, original IRI preservation, and the pinned catalog asset boundary.

Passed:

- `./gradlew :web-server:test --no-daemon`
- `./gradlew test --no-daemon`
- `(cd web-app && npm test && npm run build)`

## Commit

The implementation commit is created on the Slice 8 branch and is reported with the implementation result.

## Assumptions And Limitations

- FIBO retrieval and indexing remain owned by the existing Phase 5 pinned package; this slice adds web adapters and does not introduce a new retrieval system.
- External proposals enter the existing in-memory shared staging session and still require the existing preview, review, approval, and apply workflow.
- The web endpoint namespace uses the existing `/api/v1/projects/{projectId}/external/fibo/...` boundary while retaining the ExecPlan’s FIBO resource responsibilities.
- Pagination is exposed through the existing offset/limit contract; the UI provides scrollable lists and can consume continuation metadata for later pagination controls.
