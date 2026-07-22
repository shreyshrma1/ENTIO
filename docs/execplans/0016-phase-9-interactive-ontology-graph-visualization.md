# ExecPlan: Phase 9 Interactive Ontology Graph Visualization

## Status

Approved for implementation.

Implementation must proceed one slice at a time in the exact dependency order below.

Slice 0 remains a mandatory feasibility gate for the frontend graph approach. If the preferred dependency cannot satisfy the required interaction and accessibility behavior, implementation must stop and the approved documents must be amended before continuing.

## Related Spec

- [Phase 9 Interactive Ontology Graph Visualization](../specs/0016-phase-9-interactive-ontology-graph-visualization.md)
- [Phase 9 Scope](../architecture/phase-9-scope.md)

## Approved Phase Decisions

- The Phase 9 spec is approved.
- All allowed local ontology sources are selected by default.
- The graph renders classes, object properties, datatype properties, and individuals.
- The graph does not render literals, annotation properties, SHACL shapes, inferred-only entities, or external catalog entities as boxes in Phase 9.
- The initial map load is centered on the currently selected supported local entity when one exists. Otherwise, Entio loads the bounded root-class overview.
- Reopening an existing map tab focuses it without reloading unless the graph is stale.
- Phase 9 uses opaque, in-memory continuation IDs scoped to user, project, selected sources, graph fingerprint, query type, and query parameters.
- Continuation state expires after a bounded period and is cleared on server restart.
- Temporary graph state survives switching between Explore tabs and opening entity-detail tabs.
- Temporary graph state is discarded when the map tab closes, the project changes, the page reloads, the application restarts, or a stale graph is explicitly refreshed.
- The named development hardware baseline must be recorded in Slice 0 before Slice 1 begins.
- `@xyflow/react` is the preferred single frontend dependency, subject to Slice 0. If it cannot meet the required scroll, zoom, reachability, and accessibility behavior without fragile overrides, use a focused React/SVG implementation with no graph dependency and amend the spec and ExecPlan before Slice 1.

## Approved Graph Limits

- Initial response: at most 75 nodes and 150 edges.
- One expansion response: at most 50 new nodes and 100 new edges.
- One open map tab: at most 300 nodes and 600 edges.
- Search results: at most 20.
- A response must never include an edge whose required endpoint is absent.
- Reaching a server page limit returns an opaque continuation when more results are available.
- Reaching the open-tab aggregate limit returns a clear limit state and blocks further expansion.
- Limits may not be increased merely to make tests pass.

## Graph Identity And Cross-Source Rules

- Graph node identity is `(sourceId, entityIri)`.
- A relationship connects to the matching entity in the same source when that declaration exists.
- If the referenced entity exists only in one other allowed local source, the relationship connects to that source-specific entity.
- If more than one allowed source declares the referenced IRI and no same-source declaration exists, Entio must not choose silently.
- Ambiguous cross-source relationships are omitted from the graph result and counted in a bounded diagnostic field.
- The semantic service must return the diagnostic count deterministically, and tests must cover it.
- React must not resolve ambiguous endpoints independently.

## Prototype Reference

`entio-ontology-graph-prototype-v10-clickable.html` is a visual and interaction reference.

Codex may reuse:

- visual hierarchy;
- interaction intent;
- node styling;
- temporary read-only pop-up behavior;
- control placement.

Codex must not copy:

- fake ontology data;
- hard-coded nodes or relationships;
- prototype-only layout calculations without tests;
- standalone navigation;
- semantic assumptions implemented in JavaScript.

The approved spec and current Entio architecture override the prototype.

## Goal

Implement a bounded, read-only ontology map as one reusable Explore tab. The Kotlin semantic layer will deterministically identify local supported entities and asserted relationships, Ktor will enforce identity/scope/permission/fingerprint boundaries and expose versioned GET contracts, and React will own temporary layout and viewport interaction while reusing the current outline, search, and entity-detail navigation.

## Current State

- `semantic-engine` already loads local ontology graphs, assembles semantic descriptors, resolves deterministic labels, and exposes class, property, and individual relationships.
- `core-types` already defines RDF-term-aware graph and semantic descriptor contracts, but it has no ontology-visualization read model.
- `web-server` already reloads registered projects for read requests, adapts descriptors into summary, hierarchy, outline, search, and entity responses, enforces development identity/authorization at Ktor routes, and computes a deterministic `webGraphFingerprint`.
- `web-app` already has a single `ProjectWorkspace` Explore surface, an outline sidebar, an in-memory entity-tab collection, query hooks, semantic search, and an `openEntity` handoff into detail tabs.
- The Explore URL currently identifies entity details with `iri`; it has no explicit map view state.
- `web-app` has no graph-rendering dependency. Its production dependencies are React, React Router, and TanStack Query.
- Existing tests cover Kotlin contracts/services, Ktor routes, React components, and Playwright end-to-end behavior, but no graph fixtures or performance harness exist.
- `docs/architecture/phase-9-scope.md` is an approved planning input and must not be modified by implementation unless separately requested.

## Target State

- A deterministic semantic graph-query service emits supported local nodes and asserted edges with fixed ordering and bounded continuation.
- Versioned Ktor GET routes expose initial/centered and neighborhood responses, validate project/source/entity/fingerprint/continuation inputs, and return safe structured errors.
- Explore opens or focuses one `Ontology Map` tab while preserving the existing outline and entity tabs.
- The map renders a deterministic layered view and supports drag, pan, two-axis scroll, pointer-anchored zoom, fit, reset, selection, read-only pop-up, details handoff, search, filters, and bounded expansion.
- Map state lives only for the lifetime of the open tab and never enters semantic staging, proposals, persistence, or collaboration state.
- Stale fingerprints block current-data actions until explicit refresh.
- Accessibility and performance gates are verified against small, medium, 500-entity, and 1,000-entity deterministic fixtures.

## Affected Modules And Files

Expected production changes are limited to existing modules and the documentation decision boundary:

- `core-types`
  - add `src/main/kotlin/com/entio/core/OntologyGraphContracts.kt`;
  - add focused construction/invariant tests.
- `semantic-engine`
  - add `src/main/kotlin/com/entio/semantic/OntologyGraphService.kt`;
  - add service tests and graph fixture helpers under existing test source sets.
- `web-server`
  - add graph web DTOs under `src/main/kotlin/com/entio/web/contract/`;
  - add a focused read adapter/service under `src/main/kotlin/com/entio/web/`;
  - extend `Application.kt`, `WebGraphFingerprint.kt` only if reuse requires visibility/API adjustment, and existing web contract tests;
  - add graph route, permission, continuation, stale, and scale tests.
- `web-app`
  - add graph contracts/API/query hooks under `src/web/`;
  - add focused map components and helpers under `src/workbench/ontology-map/`;
  - minimally extend `ProjectWorkspace.tsx`, `Icon.tsx` if an existing icon cannot serve the action, and `styles.css`;
  - update `package.json` and its committed lockfile only if the dependency spike is approved and passes;
  - add Vitest and Playwright coverage plus deterministic frontend fixtures.
- `docs`
  - add one ADR for graph ownership, limits, temporary state, and the frontend dependency decision;
  - add/update the Phase 9 implementation summary only after all completion gates pass;
  - update repository status links only in the final documentation slice.

No new Gradle module, npm workspace, server framework, storage layer, database, or shared utility package is planned.

## Slice Dependencies

- Slice 0: approved Phase 9 scope and spec.
- Slice 1: Slice 0.
- Slice 2: Slice 1.
- Slice 3: Slice 2.
- Slice 4: Slice 3.
- Slice 5: Slice 4.
- Slice 6: Slice 5.
- Slice 7: Slice 6.
- Slice 8: Slice 7.
- Slice 9: Slice 8.

No slices may be implemented in parallel.

## Implementation Slices

### Slice 0: Approval And Graph-Library Feasibility Gate

#### Goal

Resolve the source spec's open decisions and prove that the preferred focused graph dependency can satisfy the required interaction model before product implementation begins.

#### Allowed Files And Modules

- `docs/specs/0016-phase-9-interactive-ontology-graph-visualization.md`
- `docs/execplans/0016-phase-9-interactive-ontology-graph-visualization.md`
- `docs/decisions/phase-9-slice-0-feasibility.md`
- a disposable directory outside tracked source created with `mktemp -d` for the feasibility experiment
- read-only inspection of `web-app` configuration and existing components

#### Forbidden Actions And Modules

- No tracked production or test code changes.
- No dependency installation into the repository.
- No edits to Kotlin modules, server routes, existing workbench behavior, or the user-authored Phase 9 scope.
- No alternative framework may be adopted silently.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-0-feasibility.md`.
- A short feasibility record covering `@xyflow/react` license, React 19 compatibility, bundle impact, keyboard semantics, custom node rendering, two-axis scroll/pan, pointer-anchored pinch zoom, deterministic viewport testing, reduced motion, and full-node reachability at 25%, 100%, and 200% zoom.
- Confirmation of the named development-hardware baseline.
- Confirmation that all allowed local ontology sources are selected by default.
- Confirmation that the preferred dependency can satisfy the approved interaction model without fragile overrides.
- Spec/plan amendments, if needed, before Slice 1.

#### Tests

- Disposable interaction proof for wheel scroll versus pinch zoom, node drag versus click threshold, and pointer anchoring.
- Dependency audit and production build proof in the disposable environment.

#### Verification Commands

From the disposable directory, record exact commands and versions. At minimum:

```bash
npm view @xyflow/react version license peerDependencies dist.unpackedSize
npm audit --omit=dev
npm run build
```

Repository verification:

```bash
git diff --check
git status --short
```

#### Stop Conditions

- The dependency has an incompatible license, React peer range, unacceptable audited vulnerability, or cannot meet required interaction/accessibility behavior.
- The dependency cannot support a scrollable work area whose scroll bounds remain correct at 25%, 100%, and 200% zoom without fighting its viewport transform.
- Satisfying the spec requires a second graph/layout framework, canvas-only inaccessible rendering, or browser-owned semantic interpretation.
- The named performance baseline hardware is not identified.
- A focused React/SVG fallback would require behavior outside the approved spec or another dependency.

### Slice 1: Core Graph Read Contracts

#### Goal

Define immutable, UI-independent contracts for supported graph entities, asserted relationship kinds, bounded queries, continuations, and results.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-1-core-graph-contracts.md`
- `core-types/src/main/kotlin/com/entio/core/OntologyGraphContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/OntologyGraphContractsTest.kt`

#### Forbidden Actions And Modules

- No Jena, Ktor, React, JSON, CSS, layout, HTTP, filesystem, or UI types in `core-types`.
- No editing, proposal, AI, persistence, collaboration, inferred-fact, literal-node, annotation-property-node, or SHACL-node contracts.
- No changes to existing public contracts unless compilation proves a minimal additive adjustment is necessary and it is reviewed first.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-1-core-graph-contracts.md`.
- Explicit enums/sealed states for supported node kinds, edge kinds, provenance, expansion categories, and load kind.
- Immutable node identity, node summary, edge, query, page/continuation metadata, graph limits, and result contracts.
- Constructor invariants for positive limits, supported endpoints, asserted provenance, opaque continuation representation, `(sourceId, entityIri)` identity, and bounded ambiguous-cross-source diagnostics.
- Public API documentation where purpose or invariants are not obvious.

#### Tests

- Construct every supported kind and relationship.
- Reject invalid counts/limits and incomplete identities.
- Demonstrate values remain presentation-neutral and serializable by adapters without depending on adapter types.

#### Verification Commands

```bash
./gradlew :core-types:test
./gradlew :core-types:check
git diff --check
```

#### Stop Conditions

- Contracts require UI coordinates or web-specific concerns.
- A proposed type duplicates or weakens existing RDF or semantic descriptor contracts.
- Supporting required identities would reverse module dependency direction.

### Slice 2: Deterministic Local Ontology Graph Service

#### Goal

Implement bounded graph extraction and neighborhood traversal over loaded local ontology data using existing semantic descriptors and RDF terms.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-2-semantic-graph-service.md`
- `semantic-engine/src/main/kotlin/com/entio/semantic/OntologyGraphService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/OntologyGraphServiceTest.kt`
- minimal additions to existing semantic test fixture helpers
- the Slice 1 contracts

#### Forbidden Actions And Modules

- No web DTOs, HTTP, React assumptions, coordinates, serialization, or client filters.
- No raw ontology parser, custom RDF/OWL engine, reasoning implementation, or changes to graph mutation/application paths.
- No imported FIBO/external descriptors, inferred facts, literal nodes, annotation property nodes, or SHACL shapes.
- No unbounded traversal or nondeterministic collection iteration.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-2-semantic-graph-service.md`.
- A plain semantic service that classifies supported local entities from existing descriptor assembly.
- Extraction for `SUBCLASS_OF`, `DOMAIN`, object-property `RANGE`, `TYPE`, and `OBJECT_ASSERTION` facts.
- Deterministic selected-entity and root-class initial queries.
- Deterministic category-based neighborhood expansion.
- Stable de-duplication, ordering, counts, and continuation cursor state independent of HTTP encoding.
- Use the approved graph limits from this ExecPlan.
- Same-source-first endpoint resolution.
- Deterministic omission and bounded diagnostics for ambiguous cross-source endpoints.
- Safe indication of additional nodes/edges without orphan edges.

#### Tests

- Cover all supported entity/edge kinds and label fallbacks.
- Prove local-only behavior in projects with imported/external descriptors.
- Cover duplicate subclass statements, parallel assertion predicates, same-source endpoint resolution, unique cross-source endpoint resolution, and ambiguous cross-source omission diagnostics.
- Repeat identical requests to prove stable ordering and continuation.
- Cover entity-centered, root overview, source-filtered, and each expansion category.
- Verify small, medium, 500-entity, and 1,000-entity fixtures remain bounded.
- Verify service reads do not mutate `EntioProject`, graphs, or source files.

#### Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew :semantic-engine:check
git diff --check
```

#### Stop Conditions

- Existing descriptors cannot express a required asserted relationship without duplicating semantic policy.
- Correct traversal requires imported/external data or inferred facts contrary to the spec.
- Stable continuation cannot be guaranteed without durable state.
- A performance threshold is already exceeded by server-side extraction on the 1,000-entity fixture.

### Slice 3: Versioned Ktor Graph Read Boundary

#### Goal

Expose the semantic service through secure, safe, fingerprint-bound GET contracts within the existing Ktor application.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-3-web-graph-boundary.md`
- focused graph DTO file(s) under `web-server/src/main/kotlin/com/entio/web/contract/`
- a focused graph adapter/service under `web-server/src/main/kotlin/com/entio/web/`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/WebGraphFingerprint.kt` for minimal reuse/visibility changes
- focused tests under `web-server/src/test/kotlin/com/entio/web/`

#### Forbidden Actions And Modules

- No semantic traversal implemented in route handlers.
- No POST/PUT/PATCH/DELETE graph routes.
- No new authorization system, identity system, persistence, cache, WebSocket protocol, or raw source-content response.
- No filesystem paths, secrets, stack traces, Jena types, or unrestricted triples in DTOs.
- No changes to CLI, VS Code, staging, proposal, AI, reasoning, SHACL, or FIBO routes.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-3-web-graph-boundary.md`.
- `GET /api/v1/projects/{projectId}/graph` and `/graph/neighborhood` routes.
- Versioned DTO mapping with safe source display identity and deterministic edge/node IDs.
- Reuse of registered-project loading, development identity, `BROWSE` permission, and local graph fingerprint.
- Opaque server-owned in-memory continuation IDs bound to user, project, selected sources, graph fingerprint, query type, and query parameters.
- Bounded continuation expiry and safe process-restart invalidation.
- Semantic offsets and continuation internals must never be accepted as trusted browser input.
- Safe error mapping for permission, source/entity validation, invalid continuation, staleness, and load failures.
- Expected-fingerprint validation on initial and expansion reads.

#### Tests

- Contract serialization for every node, edge, limit, continuation, and error shape.
- Identity and `BROWSE` enforcement.
- Cross-project/source and imported/external entity rejection.
- Unknown, mismatched, expired, process-lost, cross-user, cross-project, and stale continuation rejection.
- Fingerprint change after an applied ontology update.
- No path/source-content leakage in success or error bodies.
- Deterministic bounded route responses for all four scale fixtures.
- Existing Ktor route regressions.

#### Verification Commands

```bash
./gradlew :web-server:test
./gradlew :web-server:check
./gradlew test
git diff --check
```

#### Stop Conditions

- The route needs a new production authentication/tenancy system beyond existing approved boundaries.
- Mapping requires moving semantic meaning into Ktor or returning raw RDF to React.
- Existing web route behavior regresses.

### Slice 4: Frontend Contracts, Query Hooks, And Explore Map Tab

#### Goal

Add typed graph reads and one map-tab lifecycle while preserving the current outline and entity-detail navigation.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-4-explore-map-tab.md`
- `web-app/src/web/contracts.ts`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- focused tests in `web-app/src/web/`
- minimal `web-app/src/workbench/ProjectWorkspace.tsx` changes
- a small map-tab shell under `web-app/src/workbench/ontology-map/`
- `web-app/src/workbench/ProjectWorkspace`-level tests or existing app tests

#### Forbidden Actions And Modules

- No rendering/interaction dependency installation in this slice.
- No raw RDF parsing, semantic edge construction, client ranking, editing callbacks, persistence, localStorage/sessionStorage, collaboration state, or duplicate outline data source.
- No sentinel fake entity IRI for the map tab.
- No redesign of unrelated modules or Explore detail panes.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-4-explore-map-tab.md`.
- TypeScript response/error types aligned with Ktor fixtures.
- Abortable initial and neighborhood API functions and TanStack Query hooks.
- Explicit URL/map view state and a discriminated Explore tab model supporting one map tab plus existing entity tabs.
- `View as map` action that focuses rather than duplicates the map.
- Centered initial load when a supported local entity is selected; otherwise bounded root-class overview.
- Existing outline rendered unchanged beside a loading/empty/error map shell.
- `View Details` callback wired to the existing `openEntity` flow.
- Per-open-tab temporary map state that survives Explore tab switching and detail-tab navigation.
- Temporary state cleared on map close, project change, page reload, application restart, or explicit stale refresh.

#### Tests

- Contract normalization and malformed-reference rejection.
- URL encoding for project/source/entity/fingerprint/continuation inputs.
- Open/focus/close map lifecycle and coexistence with entity tabs.
- Outline component/query reuse.
- Map-to-detail handoff and return preserve map shell state.
- Initial load retry and cancellation behavior.
- Existing `ProjectWorkspace` and app tests remain green.

#### Verification Commands

```bash
cd web-app
npm test -- --run
npm run build
```

Then from the repository root:

```bash
git diff --check
```

#### Stop Conditions

- The existing tab/URL structure cannot represent a map without breaking entity deep links.
- Reusing the outline requires duplicating its state/data ownership.
- Contract mismatch would require browser-side semantic inference.

### Slice 5: Graph Renderer, Layout, And Viewport Mechanics

#### Goal

Render the server graph with deterministic initial positions and implement the required drag, scroll, pan, zoom, fit, reset, and accessible focus mechanics.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-5-graph-renderer.md`
- `web-app/package.json` and committed lockfile for the single approved dependency
- components/helpers/tests under `web-app/src/workbench/ontology-map/`
- minimal additions to `web-app/src/styles.css`
- `web-app/src/components/ui/Icon.tsx` only if an existing icon cannot represent a required control
- ADR under `docs/decisions/`

#### Forbidden Actions And Modules

- No second graph/layout/gesture dependency.
- No canvas-only inaccessible node implementation.
- No server coordinates, durable positions, browser persistence, semantic calculation, editing gestures, minimap, alternate layout, animations under reduced motion, or broad workbench redesign.
- No modification of ontology, staging, proposal, AI, or collaboration code.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-5-graph-renderer.md`.
- Approved `@xyflow/react` integration when Slice 0 proves compliance, or the explicitly amended focused React/SVG alternative with no graph dependency.
- Deterministic left-to-right layered layout helper with stable tie-breaking.
- Custom accessible rectangular nodes and labeled directed edges.
- Four-pixel drag/click disambiguation and edge updates while dragging.
- Scrollable world-bounds calculation with padding after layout, drag, filter, and expansion.
- Space/pointer pan, two-axis trackpad scroll, anchored pinch/wheel zoom, 25%-200% clamping, zoom percentage, fit, and confirmed reset.
- Spatial keyboard navigation and loaded-entity list fallback.
- ADR documenting ownership, dependency rationale, limits, temporary state, and consequences.

#### Tests

- Deterministic layout snapshot/data tests for stable positions.
- Distinct text/icon treatment for all node kinds and labels for all edge kinds.
- Drag threshold and attached-edge update.
- Horizontal/vertical reachability at 25%, 100%, and 200%.
- Pointer anchoring within one CSS pixel.
- Zoom clamping, percentage, fit-visible, and reset confirmation.
- Keyboard directional focus with deterministic tie-breaking and loaded-entity list access.
- Reduced-motion behavior and accessible names/focus states.
- Dependency production build and audit review.

#### Verification Commands

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test -- --run
npm run build
```

Then from the repository root:

```bash
git diff --check
```

#### Stop Conditions

- The approved dependency cannot distinguish normal scroll from pinch zoom as specified.
- All visible nodes cannot remain reachable at every zoom.
- Required accessibility needs a second rendering/navigation framework.
- Production bundle or measured interaction misses the approved gate materially.
- The renderer begins deriving ontology meaning or emitting edits.

### Slice 6: Pop-Up, Outline/Search Handoff, Filters, And Expansion

#### Goal

Complete entity inspection and navigation using the already established graph and Explore boundaries.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-6-map-interactions.md`
- components/helpers/tests under `web-app/src/workbench/ontology-map/`
- minimal `ProjectWorkspace.tsx` integration for outline selection and existing detail handoff
- existing graph API/query files for bounded continuation support
- focused CSS additions

#### Forbidden Actions And Modules

- No permanent details sidebar, edit form, edge inspector, client semantic search, arbitrary node removal, graph-state persistence, or automatic unbounded merging.
- No changes to server semantic meaning or limits unless a verified contract defect sends work back to an earlier slice.
- No changes to existing outline results or normal entity-selection behavior outside the active map case.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-6-map-interactions.md`.
- Viewport-aware read-only node pop-up with bounded server-provided summaries and asserted provenance.
- Pop-up close/reposition rules and `View Details` handoff.
- Loaded outline/search selection focus and unloaded supported-local centered replacement confirmation.
- Collapsed node-kind, local-source, and edge-kind filters plus clear action.
- Node expansion categories, continuation, loading/partial indicators, and the approved 300-node/600-edge aggregate guard.
- Merge by stable identities without resetting unaffected manual positions.

#### Tests

- Every supported node kind opens the correct pop-up by pointer and keyboard.
- Pop-up stays visible, closes under all specified conditions, and exposes no edit control.
- Detail handoff focuses duplicate entity tabs according to current behavior.
- Outline/search loaded focus and unloaded centered replacement, including confirmation after manual movement/expansion.
- External/unsupported search result exclusion.
- Filter hide/restore semantics and edge endpoint filtering.
- Expansion categories, continuation, stable merge, positions, partial indicators, and aggregate limit messaging.
- Assert no mutation/staging/proposal API call from all map interactions.

#### Verification Commands

```bash
cd web-app
npm test -- --run
npm run build
```

Then from the repository root:

```bash
git diff --check
```

#### Stop Conditions

- Pop-up data needs an unbounded entity-detail fetch or exposes raw source data.
- Outline/search integration creates a second navigation or ranking system.
- Expansion can exceed client/server caps or merge fingerprint-mismatched data.
- A graph action can enter the editing/staging path.

### Slice 7: Staleness, Safe Failures, And Accessibility Hardening

#### Goal

Make stale and partial states explicit, ensure safe recovery, and close keyboard/screen-reader gaps before end-to-end scale work.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-7-staleness-accessibility.md`
- graph adapter/route tests in `web-server`
- graph components/query tests in `web-app`
- minimal reuse of existing project change notification signals if already available
- styles limited to graph status, focus, contrast, and reduced motion

#### Forbidden Actions And Modules

- No new WebSocket protocol required for correctness.
- No silent automatic replacement of a stale graph.
- No raw errors, stack traces, paths, or old graph presented as current.
- No new accessibility framework or broad non-graph UI redesign.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-7-staleness-accessibility.md`.
- Fingerprint included and checked on all reads/continuations.
- Stale overlay preserving old display, disabling current-data actions, and offering explicit refresh.
- Refresh clears positions/selection/expansion while retaining applicable filters.
- Retryable initial and incremental safe error states; aborted requests remain silent.
- Live-region, focus restoration, contrast, reduced-motion, and graph keyboard behavior finalized.

#### Tests

- Apply/update ontology, detect stale request, preserve old state, explicitly refresh, and verify new fingerprint.
- Stale expansion/continuation never merges.
- Each safe server error maps to appropriate client state without data loss or sensitive output.
- Automated accessibility checks available in the existing toolchain plus manual keyboard/screen-reader checklist.
- Existing Explore keyboard and tab-order tests remain green.

#### Verification Commands

```bash
./gradlew :web-server:test
cd web-app
npm test -- --run
npm run build
```

Then from the repository root:

```bash
git diff --check
```

#### Stop Conditions

- Correct staleness behavior depends solely on best-effort client notification.
- Old and new fingerprint data can coexist after any merge path.
- Keyboard users cannot reach/select every loaded visible entity.
- Error handling exposes sensitive project/source details.

### Slice 8: Scale Fixtures, Browser Regression, And Performance Gate

#### Goal

Verify the complete Phase 9 promise in a real browser and prove bounded behavior and performance without weakening limits or compatibility.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-8-scale-performance.md`
- deterministic test fixtures/resources in existing module test directories
- `web-app` Playwright specs and test-only fixture/mocking support
- focused Kotlin integration tests
- a test-only performance harness/script within existing `web-app` test infrastructure
- implementation evidence under the Phase 9 summary, after behavior passes

#### Forbidden Actions And Modules

- No production-only fixture code or runtime benchmark endpoint.
- No thresholds relaxed, limits raised, tests skipped, or timeouts inflated merely to pass.
- No unrelated product behavior, new persistence, new module, or test-only production API.
- No implementation summary claiming completion before full verification succeeds.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-8-scale-performance.md`.
- Reproducible small, medium, 500-entity, and 1,000-entity fixtures with documented entity/edge makeup.
- Playwright coverage for entry/focus, outline reuse, node kinds, all relationship kinds, click/drag distinction, empty-space close, details handoff, search, filter, pan, two-axis scroll, anchored pinch, controls, expansion, limits, staleness, permissions, accessibility, and read-only behavior.
- Performance report naming CPU, memory, OS, browser, Java, and Node versions and listing five warm runs, medians, and worst results.
- Full existing Explore regression and repository verification evidence.

#### Tests

- All source-spec test cases and acceptance criteria.
- Existing web-app unit and end-to-end suites.
- Existing web-server and semantic-engine integration suites.
- Full Gradle and TypeScript builds/checks.

#### Verification Commands

```bash
./gradlew :core-types:test :semantic-engine:test :web-server:test
./gradlew test
./gradlew build
./gradlew check
```

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test
npm run build
npm run test:e2e
```

```bash
cd vscode-extension
npm ci
npm test
```

From the repository root after all suites:

```bash
git diff --check
git status --short
```

#### Stop Conditions

- Any source-spec acceptance criterion or existing regression fails.
- The 1,000-entity fixture renders the whole project initially.
- Any performance threshold fails on the recorded baseline after focused profiling and one bounded optimization pass.
- Browser tests reveal graph actions can mutate ontology or staged/proposal state.
- Completion would require changing the approved scope, limits, dependency set, or architecture.

### Slice 9: Final Documentation And Review

#### Goal

Record the verified implementation and align current repository documentation without rewriting historical phase records.

#### Allowed Files And Modules

- `docs/decisions/phase-9-slice-9-phase-completion.md`
- `docs/phase-summaries/phase-9-summary.md`
- `README.md`
- `AGENTS.md`
- the Phase 9 spec, ExecPlan, and ADR for status/link corrections

#### Forbidden Actions And Modules

- No production/test code changes mixed into the documentation slice.
- No edits that describe unimplemented or failed behavior as complete.
- No rewriting historical Phase 1-8 records or reactivating removed native AI execution surfaces.
- No modification of `docs/architecture/phase-9-scope.md` without explicit instruction.

#### Expected Changes Or Output

- `docs/decisions/phase-9-slice-9-phase-completion.md`.
- Phase 9 implementation summary including delivered behavior, exact limits, dependency decision, files/modules, fixtures, performance evidence, verification commands/results, known limitations, and rollback notes.
- Current repository status and documentation index updated to Phase 9 only after all gates pass.
- Spec and ExecPlan marked implemented/completed only when the definition of done is satisfied.

#### Tests

- Documentation link and diff review.
- Re-run only checks affected by any final mechanical documentation tooling; do not rerun expensive suites if no code changed and prior evidence is current.

#### Verification Commands

```bash
git diff --check
git diff --stat
git status --short
```

#### Stop Conditions

- Verification evidence is incomplete or stale.
- Known failures contradict completion claims.
- Documentation would overwrite unrelated user changes.

## Test Plan

Testing proceeds from the lowest stable boundary outward:

1. `core-types` construction and invariant tests establish explicit graph states.
2. `semantic-engine` tests prove local asserted graph meaning, deterministic ordering, pagination, bounds, and immutability.
3. `web-server` contract/integration tests prove safe DTO mapping, authorization, source/entity validation, continuation integrity, fingerprints, staleness, and scale bounds.
4. `web-app` contract and component tests prove tab lifecycle, deterministic layout, viewport math, selection/pop-up, search/filter/outline handoff, expansion, stale recovery, read-only isolation, and accessibility.
5. Playwright verifies actual pointer, wheel, pinch-equivalent, keyboard, scroll, focus, and routing behavior.
6. The performance harness runs five warm production-build samples for each specified interaction on the documented hardware and reports median/worst results.
7. Full Gradle, web-app, VS Code, and existing Explore regressions ensure the additive map has not changed existing product behavior.

Tests should prefer semantic assertions and computed geometry over broad screenshots. Visual snapshots may supplement, but cannot replace, interaction and accessibility assertions.

## Verification Commands

Focused commands are listed per slice. Final verification is:

```bash
./gradlew test
./gradlew build
./gradlew check
```

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test
npm run build
npm run test:e2e
```

```bash
cd vscode-extension
npm ci
npm test
```

```bash
git diff --check
git status --short
```

The performance harness command must be added to this section once Slice 8 chooses its exact checked-in entry point; absence of a reproducible command blocks completion.

## Rollback Notes

- The map is additive and isolated behind the `View as map` entry point. A safe code rollback removes that entry point and graph tab first, leaving existing entity tabs and outline behavior intact.
- Remove graph queries/API calls and the two GET routes next; they have no write-side migration or persisted state to preserve.
- Remove the focused graph adapter/service and core contracts only after all consumers are gone.
- Remove `@xyflow/react` and regenerate the committed lockfile through the package manager; do not hand-edit lock entries.
- Revert the Phase 9 ADR/status documentation with the code rollback while retaining historical evidence if Phase 9 had shipped.
- No ontology, proposal, user, collaboration, or database migration rollback is required because Phase 9 stores no durable graph state and performs no semantic writes.
- If only a frontend interaction regression occurs, disable/hide the map entry point in a small reversible patch while retaining server read contracts until the fix is verified.

## Risks And Assumptions

- `@xyflow/react` is the preferred dependency, but Slice 0 must prove it can satisfy the approved interaction model. A focused React/SVG fallback is allowed only through an approved document amendment before Slice 1.
- The existing descriptor assembler may not expose every object assertion in precisely the response shape needed; the semantic service may read existing local `GraphTriple` values, but must keep RDF interpretation in `semantic-engine` and use existing RDF types.
- A project graph fingerprint currently spans the loaded graph. The implementation must confirm it excludes external catalog material and is stable for the selected local-source scope; otherwise add a local-project fingerprint helper rather than weakening stale checks.
- Multiple local sources can declare the same IRI. Identity includes source ID. The approved same-source-first and ambiguous-omission rules must be implemented and tested before exposing contracts.
- Native two-axis scrolling plus graph-library viewport transforms is the highest interaction risk; Slice 0 and geometry tests address it before feature build-out.
- JSDOM cannot validate real wheel/pinch/scroll geometry. Playwright coverage is required.
- Performance depends on development hardware and browser version. The spec requires the baseline to be named before implementation.
- The existing authorization is a development boundary, not production tenancy. Phase 9 reuses it and does not claim production identity or durable access control.
- `docs/architecture/phase-9-scope.md` is approved planning input and must remain untouched unless explicitly requested.

## Boundary Check

- Current phase: Phase 9 has been explicitly requested for specification and planning, but remains unimplemented until approval and verification.
- Non-goals: the plan excludes graph editing, AI grouping, inferred visualization, external catalogs, durable layouts, graph export, new storage, arbitrary query, CLI/VS Code work, and production identity infrastructure.
- Speculative infrastructure: it adds no module, database, persistence, collaboration protocol, semantic job type, or new server framework. One focused frontend dependency is gated and documented.
- Module responsibility: `core-types` holds neutral contracts; `semantic-engine` owns RDF/ontology interpretation; `web-server` owns scoped read adaptation; `web-app` owns drawing and temporary interaction. `shared` remains unchanged.
- Dependency direction: engine modules do not depend on Ktor or React, and clients receive structured output rather than semantic-library types.
- Standards: existing RDF terms, semantic descriptors, Jena-backed loading, and reasoning boundaries are reused. No RDF, OWL, SHACL, parser, or reasoner is reinvented.

## Definition Of Done

Phase 9 is done only when:

- the source spec and dependency decision are approved;
- every slice's expected output, named completion artifact, and test set is complete without crossing its forbidden boundaries;
- all 18 source-spec acceptance criteria are demonstrated;
- initial, expansion, and aggregate bounds are enforced and stable;
- stale, invalid, cross-project, cross-source, and external access is rejected safely;
- graph interactions create no semantic mutations or staged/proposal state;
- keyboard, screen-reader-oriented semantics, contrast, focus, and reduced motion are verified;
- all source-spec performance thresholds pass on the named hardware with retained evidence;
- focused, full Gradle, web-app, Playwright, and VS Code regression commands pass;
- an ADR and Phase 9 summary record the delivered architecture and evidence;
- final diff review shows no unrelated user files were modified and `git diff --check` is clean.

## Implementation Details To Resolve During Approved Slices

No product-scope question blocks implementation.

Slice owners must record evidence-based answers for:

- the named development hardware baseline in Slice 0;
- exact package and class names after inspecting the current repository;
- the exact continuation expiry period, within the approved in-memory continuation design;
- the exact checked-in performance harness command in Slice 8;
- any minimal additive public-contract adjustment required by compilation evidence.

These are implementation details inside the approved plan, not permission to expand scope.

If an answer requires a second frontend dependency, new framework, new storage layer, semantic behavior outside the spec, graph editing, durable collaborative layout state, or another forbidden area, stop and amend the approved documents before implementation.