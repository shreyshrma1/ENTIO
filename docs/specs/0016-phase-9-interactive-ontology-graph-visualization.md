# Feature Spec: Phase 9 Interactive Ontology Graph Visualization

## Status

Implemented through the completed ordered Phase 9 ExecPlan. Delivery evidence is recorded in the [Phase 9 implementation summary](../phase-summaries/phase-9-summary.md).

## Problem

Entio's Explore workspace presents a useful outline and detailed view of one ontology entity at a time, but it does not show how a local ontology fits together as a connected model. Users must mentally combine class hierarchy, property domain and range, individual type, and object-property assertion information across separate entity tabs.

Phase 9 adds a read-only ontology map inside Explore so users can understand and navigate those connections without creating a second semantic engine or another editing surface.

## Goals

- Add one project-scoped `Ontology Map` tab to the existing Explore workspace.
- Reuse the existing Explore outline, entity identities, semantic labels, search behavior, and entity-detail tabs.
- Visualize bounded sets of local classes, object properties, datatype properties, and individuals as labeled boxes.
- Visualize asserted class hierarchy, property domain and range, individual type, and individual-to-individual object-property relationships as labeled directed edges.
- Let users select, drag, pan, scroll, zoom, fit, reset, search, filter, and expand the loaded map without changing ontology data.
- Provide a small, temporary, read-only entity pop-up with a handoff to the existing Explore details.
- Detect graph staleness with the current project graph fingerprint.
- Enforce project, source, and browse-permission boundaries on every graph request.
- Keep initial and incremental graph loads bounded and deterministic.
- Provide deterministic engine, server, frontend, accessibility, performance, and end-to-end coverage.

## Non-Goals

- Editing ontology entities or relationships from the map.
- Dragging to create edges, deleting graph content, or changing labels and definitions.
- Rendering literal values as nodes.
- Rendering annotation properties or SHACL shapes as nodes in the first release.
- Rendering inferred relationships in the first release.
- Visualizing imported or external catalog entities, including FIBO entities, even when the project imports them.
- AI-generated clusters, inferred visual groups, source group boxes, or semantic grouping presented as ontology fact.
- Persistent diagrams, durable positions, shared collaborative layouts, or layout synchronization.
- Graph export, arbitrary SPARQL, a graph database, 3D rendering, or a replacement for Explore navigation.
- Relationship inspection pop-ups or editable edges.
- New CLI or VS Code graph commands.

The current phase-level boundaries and non-goals in `AGENTS.md` remain in force and are not duplicated here.

## Proposed Behavior

### 1. Explore Entry And Tab Lifecycle

Explore provides a `View as map` action using the existing workbench control style.

Activating the action:

- opens an `Ontology Map` tab for the current project;
- focuses the existing map tab when it is already open;
- leaves the current Explore outline sidebar visible and backed by its existing data source;
- coexists with normal entity-detail tabs;
- records map selection in project-local client state rather than pretending the map is an ontology entity.

At most one map tab exists per project workspace. The URL distinguishes the map from an entity detail using an explicit Explore view parameter, rather than a sentinel IRI. Opening a detail from the map uses the existing entity route and tab-opening behavior. Returning to the still-open map restores its client state.

The map's loaded data, positions, viewport, filters, search text, selection, and expansion history survive switching among open Explore tabs. Closing the map tab discards that state. Page reload and server restart may also discard it. Phase 9 does not use browser storage or server persistence for graph state.

### 2. Supported Nodes

The map contains only browsable entities declared in the current project's loaded local ontology sources:

- classes;
- object properties;
- datatype properties;
- individuals.

Each node has a stable identity composed of project ID, source ID, entity IRI, and entity kind. A response never substitutes a display label for identity.

Each node shows:

- preferred label, with the existing deterministic IRI-derived fallback;
- a visible type marker and type name (`C` Class, `P` Object property, `D` Datatype property, or `I` Individual);
- a one-line definition excerpt when present, otherwise a short type-specific subtitle;
- a visual selected/focused state.

Type distinction uses text and shape/icon treatment as well as color.

### 3. Supported Edges

The server derives asserted edges from existing semantic descriptors and local graph statements. React renders the supplied meaning and must not infer new relationships.

The first release supports:

| Edge kind | Source | Target | Display label |
| --- | --- | --- | --- |
| `SUBCLASS_OF` | class | direct superclass | `subclass of` |
| `DOMAIN` | object or datatype property | class | `domain` |
| `RANGE` | object property | class | `range` |
| `TYPE` | individual | asserted class | `type` |
| `OBJECT_ASSERTION` | individual | individual | preferred property label, with deterministic IRI fallback |

Datatype ranges are not nodes in Phase 9, so a datatype property's datatype range is shown in node/pop-up metadata rather than as an edge. Class-to-direct-subclass is represented by the inverse view of the same `SUBCLASS_OF` fact and must not produce a duplicate edge.

Every edge includes a stable response ID, kind, source and target node identities, label, source ID, and `ASSERTED` provenance. Parallel object assertions remain distinct by predicate. Exact duplicate facts collapse deterministically.

### 4. Deterministic Initial Graph

The initial request uses one of two deterministic seeds:

1. If Explore has a current local supported entity, seed the graph with that entity.
2. Otherwise seed it with local root classes ordered by preferred label and then IRI.

For an entity seed, the server returns the seed plus its directly connected supported local entities. For root-class seeding, the server returns roots and breadth-first direct subclass connections, then directly connected supported properties, until a limit is reached. Individuals are loaded initially only when connected to an entity seed; overview loading does not fill remaining capacity with unrelated individuals.

Ordering is stable by relationship-kind order, display label, source ID, and IRI before limits are applied.

Initial limits are:

- at most 75 nodes;
- at most 150 edges.

The response states whether more nodes or edges are available and supplies opaque continuation tokens for deterministic follow-up reads. It never partially returns an edge whose endpoint is absent.

### 5. Expansion And Aggregate Limits

An entity node exposes `Expand connections` when more supported local relationships exist than are loaded. Expansion can request these bounded categories:

- direct superclasses and subclasses;
- domain and range connections;
- asserted types and directly typed individuals;
- incoming and outgoing object-property assertions.

One expansion response adds at most 50 nodes and 100 edges. Continuation is explicit and bound to project ID, project fingerprint, seed entity, category set, and stable ordering. Invalid, mismatched, or stale continuation tokens fail without changing the currently displayed graph.

One map tab may hold at most 300 nodes and 600 edges. Before an expansion would exceed either aggregate limit, the UI explains the limit and does not issue or merge an unbounded request. Users may filter the current view, start a new map centered on a search/outline result, or close and reopen the map; Phase 9 does not implement arbitrary node deletion as a layout-management feature.

### 6. Layout And Rendering

The map uses a deterministic left-to-right class tree: root classes, subclasses, then deeper subclasses. Asserted `SubclassOf` edges alone establish the main tree. Multiple asserted parents and other cross-branch relationships remain visible but do not control tree placement.

Sibling classes are ordered by descending direct subclass count, connected property count, directly typed individual count, and total incoming/outgoing relationship count, then by label and IRI. These facts affect presentation only. Identical graph fingerprints and view settings produce identical positions.

Object properties stay close to their asserted domain and connect to their asserted range. Datatype properties render as compact items below their asserted domain. The shared Individuals filter is selected initially, so bounded typed individuals are visible at launch and may be hidden explicitly. Large loaded branches are initially collapsed behind child counts and bounded expand/load-more actions.

The toolbar provides `Focus` and `Full map` modes, with `Full map` selected by default. `Focus` is available after selecting an entity and shows only that entity's asserted neighborhood. `Full map` shows the currently loaded bounded graph without allowing cross-links to control class-tree placement. The mode switcher sits at the left of the map toolbar, while zoom and view controls align to the right. Radial layout is not prioritized.

Selection emphasizes only asserted context: a class's parents, children, properties, and directly related classes; a property's domains and ranges; or an individual's asserted types and direct assertions. Unrelated branches and cross-links are visually subdued.

Users may manually reposition nodes after layout. Attached edges update during dragging. Expanded/collapsed state and manual positions survive while the map tab remains open. A bounded branch expansion inserts new nodes near their parent and preserves existing coordinates instead of rerunning the entire layout. A drag exceeding four CSS pixels is treated as a drag and must not also select/open the node.

The mandatory Slice 0 feasibility gate selected a focused React/SVG renderer using the repository's existing React stack and no graph, layout, or gesture dependency. The preferred `@xyflow/react` candidate passed license, React 19, audit, and build checks, but its transform-owned viewport did not make an external native scroll world's bounds follow zoom without a second, fragile geometry system. React therefore owns accessible SVG node and edge presentation plus the bounded deterministic layered layout, while server responses remain the only source of semantic meaning.

The minimap and alternate radial/automatic layouts are deferred.

### 7. Viewport Interaction

The map work area supports:

- node dragging;
- panning with a dedicated pan gesture and Space plus pointer drag;
- ordinary vertical and horizontal trackpad scrolling across the graph work area;
- pointer-anchored wheel/pinch zoom;
- `Zoom in`, `Zoom out`, `Fit loaded graph`, and `Reset view` controls;
- a visible zoom percentage.

Supported zoom is 25% through 200%, with 100% as reset. Zoom clamps at these limits.

Pointer-anchored zoom preserves the graph coordinate beneath the pointer or pinch center. The implementation maintains scrollable world bounds from node geometry plus padding after layout, drag, expansion, filtering, and zoom so every visible loaded node remains reachable horizontally and vertically at every supported zoom.

Dragging empty map space with either the left or right mouse button pans the viewport. Right-drag also pans when initiated over a node and suppresses the browser context menu; left-dragging a node continues to move that node rather than the viewport.

`Fit loaded graph` fits visible nodes, not filtered-out nodes. `Reset view` reapplies the deterministic layout and returns to 100%, after a confirmation only when manual node positions would be discarded.

### 8. Selection And Information Pop-Up

Clicking or keyboard-activating a visible node selects it and opens one temporary read-only pop-up anchored beside the node. Selecting another node replaces it. Clicking empty graph space, pressing Escape, changing Explore tabs, or closing the map closes the pop-up.

The pop-up remains within the visible graph viewport where practical and repositions after viewport movement. During a selected-node drag it closes; selection remains and the user may activate the node again.

The pop-up uses data already present in the graph response and shows:

- preferred label;
- entity kind and `Asserted` provenance as subdued text below the label;
- bounded definition excerpt;
- a compact `Details` box containing inline direct-subclass, loaded-relationship, and available-relationship counts;
- one `View Details` action.

The close control is a small icon button in the top-right corner. Clicking or pointer-pressing anywhere outside the card dismisses it. The compact card can be dragged by its title area, remains constrained to the map, and preserves its temporary position while the map tab stays open. Neighborhood expansion, hierarchy, schema, type, and assertion actions do not appear in this summary; the existing entity details view remains the richer inspection surface.

`View Details` opens or focuses the existing Explore entity-detail tab using the stable IRI and source ID, closes the pop-up, and leaves the map tab and its state open.

Double-clicking a map node does not open entity details. Full details are available only through the summary card's explicit `View Details` action.

### 9. Outline Integration

The current Explore outline remains unchanged and authoritative.

While the map tab is active, selecting a supported local outline entity:

- selects, reveals, and opens the pop-up when it is loaded;
- otherwise requests a new bounded entity-centered initial graph and replaces the map's loaded graph after confirmation when the current map contains manual positions or expansions.

This entity-centered load is not merged into an unrelated graph because doing so could exceed bounds or create a misleading disconnected view. Unsupported, imported, or external outline selections continue through normal Explore detail behavior and are not mapped.

### 10. Search And Filters

The map toolbar uses the existing server semantic search. React may debounce input and filter already-loaded response data, but it must not implement independent semantic ranking.

Search behavior is:

- matching loaded nodes are highlighted;
- choosing a loaded result reveals and selects it;
- choosing a supported local result that is not loaded offers `Open centered map`, which performs the bounded replacement behavior described above;
- external/imported and unsupported results may open their existing details but are not loaded into the map.

One compact filter icon sits beside the Project Outline entity search. Its popover supports shared entity-kind and source visibility for the project outline, semantic search results, and ontology map, plus map-only edge-kind filters for the five supported edge kinds. Clicking outside closes the popover. `Reset filters` checks every supported entity, relationship, and source filter, including individuals. The map does not render a duplicate filter panel.

Filtering changes only visible client presentation. It does not modify the server graph, ontology, staging, proposals, or stored map positions. Shared entity filters update outline counts/lists and map visibility together. An edge is hidden when its kind is filtered or either endpoint is hidden.

### 11. Staleness And Refresh

Every graph response contains the deterministic current local project graph fingerprint. Initial, expansion, and centered-load requests may include the fingerprint the client expects.

If the fingerprint is no longer current, the server returns a structured `stale-graph` error with the current fingerprint but no replacement graph data. The open map keeps its old rendered state behind a clear stale overlay and disables expansion and `View Details` claims that would present it as current. `Refresh map` reloads from the current ontology, clears positions/selection/expansion state, preserves applicable filters, and reapplies deterministic layout.

Project change notifications may proactively mark the map stale, but correctness must not depend on WebSocket delivery.

### 12. Accessibility

All toolbar controls have accessible names and visible focus states. Status, limit, loading, stale, and error messages use appropriate live-region semantics without repeatedly announcing pointer movement.

The practical graph keyboard model is:

- Tab moves through toolbar controls, then the graph, then the pop-up;
- when the graph has focus, Home focuses the deterministic first visible node;
- Arrow keys move focus to the nearest visible node in that spatial direction, with stable label/IRI tie-breaking;
- Enter or Space selects the focused node and opens its pop-up;
- Escape closes the pop-up and retains graph focus;
- `+` and `-` invoke zoom controls when graph focus is active;
- a `Loaded entities` combobox/list provides a non-spatial keyboard-accessible way to focus any visible loaded node.

Node names include label and kind. Edge meaning is available through the selected node's accessible relationship summary rather than color or line inspection alone. Reduced-motion preference disables animated layout and viewport transitions.

### 13. Performance Expectations

Deterministic small, medium, 500-entity, and 1,000-entity local ontology fixtures are required. Large fixtures must demonstrate that initial responses respect 75/150 limits and that the client does not render the full project by default.

Completion thresholds are measured in a documented production build on the repository's supported development hardware, with browser devtools closed and five warm runs reported by median and worst run:

- initial graph API response for the 1,000-entity fixture: median at most 500 ms and no run over 1,000 ms;
- initial render of a 75-node/150-edge response: median at most 500 ms and no run over 1,000 ms after response receipt;
- pop-up open and loaded-search focus: visual response within 100 ms;
- drag, pan, scroll, and zoom: no long task over 100 ms during the scripted interaction and at least 50 rendered frames per second over the measured five-second sample;
- one 50-node/100-edge expansion merged and laid out: median at most 750 ms and no run over 1,500 ms after response receipt;
- detail-tab handoff: existing tab is focused synchronously, or a newly requested entity shows its loading state within 100 ms.

If supported development hardware is not yet documented, the implementation record must name CPU, memory, OS, browser, Java, and Node versions used. Threshold failure is a stop condition, not grounds to increase limits silently.

### 14. Compatibility And Read-Only Guarantee

The map is additive. Existing Explore details, outline behavior, editing, staged changes, proposals, validation, reasoning, SHACL, FIBO browsing, provider settings, collaboration, CLI, and VS Code behavior remain unchanged.

Map HTTP routes are GET-only. Graph components receive no editing callbacks. No map interaction creates a typed edit, staged entry, proposal, activity event that claims a semantic change, source write, or RDF mutation.

## Inputs

### Initial Graph Request

`GET /api/v1/projects/{projectId}/graph`

Inputs:

- optional `sourceId` repeated parameter, restricted to registered local ontology sources;
- optional `seedIri` and matching `seedSourceId`;
- optional `expectedFingerprint`;
- optional opaque `continuation` for additional initial-page data.

Absent source IDs mean all browsable local ontology sources. A seed must identify a supported local entity inside that scope.

### Expansion Request

`GET /api/v1/projects/{projectId}/graph/neighborhood`

Inputs:

- required `entityIri` and `sourceId`;
- repeated bounded category values;
- required `expectedFingerprint`;
- optional opaque `continuation`.

The server uses fixed limits; the browser cannot request arbitrary page sizes.

### Existing Inputs Reused

- project registration and current development identity;
- `BROWSE` permission;
- existing outline and semantic-search requests;
- existing entity-detail tab identity and navigation.

## Outputs

Graph responses are versioned web DTOs and contain:

- `apiVersion`;
- `projectId`;
- `graphFingerprint`;
- normalized local `sourceIds`;
- seed identity and load kind;
- deterministically ordered nodes;
- deterministically ordered edges;
- node/edge counts and aggregate limits;
- `hasMoreNodes`, `hasMoreEdges`, and opaque continuation where applicable.

Node DTOs contain stable entity identity, kind, label, definition excerpt, safe source display identity, bounded type-specific summaries, and loaded/available relationship counts. Edge DTOs contain stable identity, edge kind, endpoint identities, label, source ID, and asserted provenance.

Errors use the existing `WebErrorResponse` and request ID boundary. They never expose filesystem paths, source contents, secrets, credentials, stack traces, or raw library errors.

## Validation Behavior

The server validates every request deterministically before graph extraction:

- project is registered and in the current identity's scope;
- current user has `BROWSE` permission;
- requested source IDs exist, belong to the project, and are local ontology sources;
- seed and expansion entities exist in the requested local scope and have a supported kind;
- expansion category values are recognized;
- expected fingerprint matches the current local project graph;
- continuation is well-formed, server-issued, project-bound, fingerprint-bound, and request-bound;
- response limits and stable ordering are applied server-side;
- every returned edge has two returned or already-requested supported endpoints;
- imported/external descriptors and literal resources are excluded.

Client validation prevents aggregate merges beyond 300 nodes or 600 edges and rejects malformed response references without replacing the last valid graph.

## Error Behavior

The server maps failures to safe structured codes:

- `unknown-project` or `project-forbidden` for invalid project scope;
- `browse-forbidden` when permission is absent;
- `missing-graph-source` or `graph-source-forbidden` for invalid source scope;
- `missing-graph-entity` for an unknown supported local entity;
- `unsupported-graph-entity` for an excluded kind or external entity;
- `invalid-graph-category` for an unknown expansion category;
- `invalid-graph-continuation` for invalid or mismatched continuation;
- `stale-graph` for fingerprint mismatch;
- `project-load-failed` or `graph-read-failed` for safe load/extraction failures.

The client keeps the last valid graph when an expansion, search-centered load, or refresh fails. Initial-load failure shows a retryable empty state. Limit states explain the applicable cap. Abort/cancellation during tab changes is not shown as an error.

## Test Cases

### Semantic And Contract Tests

- Extract each supported node kind from local sources with deterministic labels and source identities.
- Exclude annotation properties, literals, imported descriptors, FIBO catalog entries, blank-node-only structures, and unsupported entities.
- Extract and label each supported asserted edge kind.
- Collapse duplicate facts while preserving parallel object assertions with different predicates.
- Never duplicate subclass facts as both subclass and direct-subclass edges.
- Produce stable node, edge, and continuation ordering across repeated runs.
- Seed from a selected entity and otherwise from ordered root classes.
- Enforce 75/150 initial, 50/100 expansion, and 300/600 client aggregate limits.
- Return only edges whose endpoints are available.
- Reject stale fingerprints, tampered continuations, invalid categories, invalid source IDs, external entities, and cross-project IDs.
- Return safe errors without paths or ontology contents.

### Frontend Unit And Integration Tests

- `View as map` opens or focuses exactly one map tab.
- The existing outline remains present and uses its current query/component path.
- Supported node kinds render with text markers independent of color.
- Each supported edge kind renders the server label and direction.
- Click and keyboard activation open the correct read-only pop-up for every node kind.
- More than four pixels of drag does not trigger click selection; edges follow moved nodes.
- Empty-space click, Escape, and tab change close the pop-up.
- `View Details` focuses or opens the correct existing entity tab and preserves map state.
- Outline selection reveals a loaded node and offers bounded centered replacement for an unloaded node.
- Search highlights/focuses loaded results and offers centered loading for supported local missing results.
- The filter panel starts collapsed; filters hide and restore nodes/edges without API mutation calls.
- Vertical and horizontal scrolling reach every visible node at 25%, 100%, and 200% zoom.
- Pointer-anchored zoom preserves the graph coordinate under the pointer within one CSS pixel.
- Zoom controls, fit, reset confirmation, percentage, and clamping work.
- Expansion continuation merges deterministically and refuses aggregate overflow.
- Stale overlay retains the old graph, disables current-data actions, and refreshes explicitly.
- Temporary state survives Explore tab switching and is discarded on map close.
- Keyboard spatial navigation and loaded-entity list reach every visible node.
- Reduced-motion preference prevents animated transitions.
- No graph gesture calls staging, proposal, edit, apply, or source-write APIs.

### Server Integration And Security Tests

- Graph routes require identity, registered project scope, and `BROWSE`.
- Cross-project, invalid-source, unsupported-entity, and stale requests return safe errors.
- A graph response changes fingerprint after an applied ontology change; old expansion requests fail stale.
- Small, medium, 500-entity, and 1,000-entity fixtures remain bounded.
- Existing summary, outline, hierarchy, search, entity, staging, reasoning, SHACL, FIBO, and provider routes retain behavior.

### End-To-End And Performance Tests

- Complete the scope's entry, outline, selection, drag, pan, scroll, zoom, pop-up, details, search, filter, expansion, limit, stale, and read-only scenarios in a real browser.
- Run the documented performance protocol against all four fixtures and retain results with the implementation summary.
- Run all existing Explore and full repository checks.

## Acceptance Criteria

1. Explore exposes `View as map` and maintains no more than one map tab for the current project.
2. The existing outline sidebar remains visible and authoritative in the map view.
3. The map displays real, supported, local ontology entities and asserted relationships from server-owned semantic interpretation.
4. Classes, object properties, datatype properties, and individuals are distinguishable without color alone.
5. Initial and expansion responses are deterministic and respect 75/150 and 50/100 limits; the client respects the 300/600 aggregate cap.
6. Users can drag nodes and attached edges update without creating clicks or ontology changes.
7. Users can reach every visible loaded node through horizontal/vertical scrolling and panning at 25% through 200% zoom.
8. Pointer/pinch zoom remains anchored within one CSS pixel, and explicit zoom, fit, and reset controls work.
9. Pointer and keyboard selection open a bounded read-only pop-up beside every supported node kind.
10. `View Details` opens or focuses the correct existing Explore detail tab and preserves the map tab state.
11. Existing semantic search, collapsed client filters, outline focus, and bounded centered loading work as specified.
12. Fingerprint mismatch marks the map stale and requires explicit refresh before current-data actions continue.
13. Server authorization and validation prevent cross-project, cross-source, external, and unsupported access.
14. Graph routes and interactions cannot create ontology edits, staged changes, proposals, source writes, or semantic activity.
15. Accessibility behavior and reduced-motion support pass deterministic frontend and browser tests.
16. The documented 1,000-entity fixture meets the response, render, interaction, and expansion thresholds.
17. Unit, integration, frontend, end-to-end, build, and existing Explore regression checks pass.
18. Existing Kotlin engine, CLI, VS Code, editing, proposal, validation, reasoning, SHACL, FIBO, collaboration, and provider-setting behavior remains unchanged.

## Boundary Check

- Phase fit: the user explicitly requested Phase 9 planning, and the work is an additive read-only Explore capability within the existing Ktor and React surfaces.
- Current non-goals: the spec excludes native AI execution, ontology editing from the graph, durable persistence, new storage, external indexing, and other deferred infrastructure.
- Architecture: Kotlin owns entity identity and relationship meaning; Ktor adapts bounded read contracts; React owns presentation and temporary interaction state.
- Dependency direction: no engine module depends on `web-server` or `web-app`; `shared` receives no product logic.
- Standards: graph extraction reuses existing RDF parsing and semantic descriptors. It does not create a custom RDF, OWL, SHACL, or reasoning implementation.
- Speculative infrastructure: no new module, database, persistence layer, collaboration protocol, CLI boundary, or VS Code surface is required.

## Open Questions

No product-scope question blocks implementation. All allowed local ontology sources are selected by default, and Slice 0 records the development-hardware baseline and dependency-free React/SVG decision. The approved four-pixel drag threshold remains subject to verification on real pointer devices without changing scope.

Phase 9 uses asserted-only facts; no literals, annotation properties, SHACL shapes, inferred edges, minimap, or alternate layouts; temporary state survives only while the tab remains open; outline/search misses use bounded centered replacement; and the keyboard model and performance gates are defined above.
