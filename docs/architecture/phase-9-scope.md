# Phase 9 Scope

## Phase Name

**Phase 9: Interactive Ontology Graph Visualization**

## Status

Approved. Phase 9 is the current active phase. Its behavior is not implemented until the approved specification and ExecPlan are completed and verified.

## Purpose

Phase 9 adds an interactive graph view to Entio's existing Explore experience.

The graph view should let a user visually understand how local ontology classes, individuals, object properties, datatype properties, and their relationships fit together. It should feel like a natural extension of the current Explore screen rather than a separate application.

The user should be able to:

- open the graph from the existing Explore view;
- reuse the existing project outline sidebar;
- see ontology entities as boxes;
- see relationships as labeled connections;
- move boxes around;
- pan and scroll across the work area;
- pinch to zoom around the current pointer position;
- click any visible ontology box;
- view a small, temporary, read-only information pop-up beside the selected box;
- open the entity's existing Explore detail tab from that pop-up;
- filter and search the graph without changing ontology data;
- switch between normal Explore and graph views without losing project context.

The interactive HTML prototype will be available as a design and interaction reference:

```text
entio-ontology-graph-prototype-v10-clickable.html
```

The prototype is not the implementation source of truth. The approved specification and existing Entio architecture remain authoritative.

## Central Product Promise

> A user can open a visual map of the current local ontology, move through it naturally, inspect any visible entity, and jump directly into the existing Explore details for deeper review.

## Problem

The current Explore experience is effective for browsing one entity at a time, but it does not provide a visual overview of how many ontology elements relate.

This makes it harder to understand:

- class hierarchies;
- property domains and ranges;
- how individuals are typed;
- how individuals are connected;
- where an entity is used;
- whether a model is fragmented or overly connected;
- how a local ontology is organized as a whole.

Protege provides graph-style visualization, but Entio should provide a more modern, integrated, and easier-to-use experience.

## Relationship To Existing Explore Experience

Phase 9 should be integrated into the existing Explore window.

The existing Explore outline sidebar remains visible and authoritative.

A new control should be added to Explore:

```text
View as map
```

Selecting it should open a new Explore tab containing the graph visualization.

The graph tab should:

- belong to the current project;
- use the same project outline sidebar;
- preserve the user's current Explore context where practical;
- coexist with existing entity-detail tabs;
- not replace the normal Explore view;
- not create a second navigation system.

The graph should be treated as another Explore representation of the same local ontology data.

## High-Level User Flow

```text
User opens Explore
→ user selects "View as map"
→ Entio opens an Explore graph tab
→ Entio loads a bounded graph view from the current local ontology
→ user pans, scrolls, zooms, searches, filters, and moves boxes
→ user clicks a class, property, individual, or other supported box
→ Entio opens a small read-only pop-up beside the box
→ user selects "View Details"
→ Entio opens or focuses that entity's normal Explore detail tab
```

## Goals

Phase 9 should:

- add an interactive graph tab inside Explore;
- reuse the existing project outline sidebar;
- source graph entities and relationships from the loaded local ontology;
- support classes, individuals, object properties, and datatype properties;
- visually distinguish entity types;
- show labeled relationships;
- support click, drag, pan, scroll, zoom, search, and filtering;
- open a temporary read-only entity pop-up beside the selected box;
- link the pop-up to the existing Explore entity-detail tab;
- keep graph state separate from ontology state;
- preserve all current ontology editing, proposal, validation, reasoning, SHACL, AI, and review behavior;
- remain usable on medium and large local ontologies through bounded loading and expansion;
- provide deterministic tests for graph data and UI behavior.

## Non-Goals

Phase 9 must not add:

- ontology editing directly inside the graph;
- drag-to-create relationships;
- deleting entities from the graph;
- changing labels or definitions from the graph;
- AI-generated group boxes;
- automatic semantic clustering presented as ontology fact;
- persistent custom graph diagrams;
- shared collaborative graph layouts;
- graph export to image, PDF, GraphML, or another format;
- a new graph database;
- replacement of the existing Explore hierarchy and entity-detail views;
- visualization of unrestricted external catalogs;
- direct editing of FIBO or imported read-only sources;
- 3D graph rendering;
- arbitrary SPARQL graph queries;
- a second semantic interpretation layer in React;
- raw RDF processing in the browser.

## Core Read-Only Rule

The graph view is a read-only visualization.

Users may:

- open the graph;
- search;
- filter;
- expand;
- collapse;
- select;
- move boxes visually;
- pan;
- scroll;
- zoom;
- open entity details.

Users may not change ontology semantics from the graph.

Moving a box changes only its temporary visual position. It must not create or update ontology statements.

## 1. Explore Integration

### View As Map Entry Point

Add a `View as map` control to the existing Explore surface.

The exact placement should match the existing Entio UI conventions. It may appear near the current view controls or Explore tab actions.

Selecting it should open an Explore tab with:

- a graph-specific title such as `Ontology Map`;
- the current project context;
- the existing outline sidebar;
- the graph toolbar;
- the graph work area.

If an ontology map tab for the current project is already open, Entio should focus it instead of opening duplicate tabs.

### Existing Outline Sidebar

The graph tab should reuse the same outline sidebar component and data source used by normal Explore.

Selecting an entity in the sidebar while the graph tab is active should:

- find the entity in the loaded graph when present;
- select it;
- center or reveal it;
- open the same temporary information pop-up.

If the entity is not currently loaded, Entio should offer or automatically perform bounded graph expansion around that entity.

The sidebar remains the authoritative project navigation surface.

## 2. Graph Data Source

### Local Ontology Only

The graph boxes must come from the current project's loaded local ontology sources.

Phase 9 should not invent entities or visual groups.

Supported graph entities should include:

- classes;
- object properties;
- datatype properties;
- individuals;
- annotation properties only if they are already represented as browsable local ontology entities and their inclusion remains visually useful.

SHACL shapes may be added later. They are not required for the first Phase 9 graph unless already approved in the specification.

### Graph Relationships

The graph should use existing semantic-engine and read-only project services to produce relationships.

At minimum, support:

- class to superclass;
- class to direct subclass where useful;
- object property to domain class;
- object property to range class;
- datatype property to domain class;
- individual to asserted type;
- individual to individual through object-property assertions;
- individual to literal values only when explicitly enabled by the user, because literal nodes can create excessive clutter.

Relationship labels should use existing labels where available and safe fallbacks where labels are missing.

React must not infer graph meaning independently.

### Asserted And Inferred Facts

The first graph load should default to asserted local ontology facts.

If inferred relationships are supported in Phase 9, they must:

- be visually distinguishable;
- be disabled by default unless otherwise approved;
- come from existing Entio reasoning services;
- never be presented as asserted facts.

A simple filter such as `Show inferred` may be included only if the existing reasoning result is current and available.

## 3. Initial Loading And Expansion

Loading an entire large ontology into the browser may create an unreadable or slow graph.

Phase 9 should therefore use bounded graph loading.

### Initial Graph

The initial graph may be built from:

- the currently selected entity and its neighborhood;
- the currently visible outline section;
- a bounded project overview;
- root classes and a limited number of related properties and individuals.

The specification should choose one deterministic default.

### Expansion

Users should be able to expand the graph from an entity.

Possible actions:

- show direct subclasses;
- show superclasses;
- show related properties;
- show typed individuals;
- show incoming relationships;
- show outgoing relationships;
- load more nearby entities.

Expansion must use bounded server queries and existing semantic services.

The UI should clearly indicate when more relationships exist but are not currently loaded.

### Limits

The specification should define:

- maximum initial node count;
- maximum initial edge count;
- maximum expansion count;
- maximum graph node count before warning or blocking further expansion;
- pagination or continuation behavior.

The graph must fail safely and explain when a limit is reached.

## 4. Visual Representation

### Entity Boxes

Each entity should appear as a rectangular box.

Boxes should show at least:

- entity label;
- entity type indicator;
- a short type-specific subtitle or definition excerpt when space allows.

Entity types must be visually distinct without relying only on color.

Recommended markers:

- `C` for class;
- `P` for object property;
- `D` for datatype property;
- `I` for individual.

Color and icon treatment should follow Entio's existing design language.

### Relationship Lines

Connections should:

- clearly connect the appropriate boxes;
- show a readable label;
- use arrows where direction matters;
- avoid covering node text;
- update when boxes move;
- remain selectable only if the specification requires relationship inspection.

The first release does not require editable edges.

### Layout

The graph should provide one deterministic default layout.

Optional layouts may include:

- automatic;
- hierarchy;
- radial.

The specification should decide which layouts are required for Phase 9.

Users must be able to manually reposition boxes after layout.

Manual positions are temporary view state unless later persistence is approved.

### No Group Boxes

Phase 9 should not include boxes around guessed groups.

No `Parties`, `Core Banking`, `Agreements`, or similar regions should appear unless they come from a future explicitly approved grouping feature.

Ontology modules and source boundaries may be shown through filters or metadata, not large group boxes in the first release.

## 5. Graph Interaction

### Select

Clicking any visible entity box should select it.

Supported boxes include:

- classes;
- object properties;
- datatype properties;
- individuals;
- any other entity kind explicitly included by the specification.

Selection should:

- visually highlight the box;
- open the temporary information pop-up;
- close the previous pop-up;
- preserve the graph position.

Clicking empty graph space should close the pop-up and clear selection.

### Drag

Users should be able to drag entity boxes.

Dragging should:

- move only the visual box;
- update attached lines immediately;
- not change ontology data;
- not open the information pop-up when the gesture was clearly a drag.

A normal click without meaningful movement should open the information pop-up.

### Pan And Scroll

The graph work area should support:

- vertical scrolling;
- horizontal scrolling;
- pan mode;
- spacebar-assisted panning where appropriate.

A user must be able to reach every loaded box at every supported zoom level.

The implementation must not allow zoom transforms to reduce or incorrectly calculate the scrollable bounds.

### Zoom

Zoom should use trackpad pinch or an equivalent browser zoom gesture inside the graph.

Normal vertical scrolling must scroll vertically.

Normal horizontal trackpad movement must scroll horizontally.

Pinch zoom must use the pointer or pinch center as the zoom origin:

> The graph point under the pointer before zoom must remain under the pointer after zoom.

The graph should also provide explicit zoom controls:

- zoom in;
- zoom out;
- fit loaded graph;
- reset or center view;
- visible zoom percentage.

The specification should define supported minimum and maximum zoom.

### Minimap

A minimap may be included if it improves navigation and remains accurate.

If included, it should:

- show the relative location of loaded boxes;
- show the current visible viewport;
- update when boxes move or zoom changes;
- allow bounded navigation if practical.

The minimap is optional unless required by the specification.

## 6. Temporary Entity Information Pop-Up

Clicking an entity box should open a small temporary pop-up beside it.

This must not be a permanent right sidebar.

The pop-up is read-only.

### Positioning

The pop-up should:

- appear next to the selected box;
- choose the left or right side based on available space;
- remain fully visible inside the graph viewport where practical;
- move or close if the selected box is moved;
- close when the user clicks outside it, selects another entity, changes tabs, or presses Escape.

### Required Information

The pop-up should show a bounded overview using existing Entio entity-detail data.

At minimum:

- preferred label;
- entity kind;
- source;
- definition excerpt where available;
- direct superclass or asserted type summary where relevant;
- a small related-entity summary;
- asserted or inferred provenance when applicable.

The pop-up must not contain editable fields.

### View Details

The pop-up should include a `View Details` button.

Selecting it should:

- open the corresponding entity's existing Explore detail tab;
- focus an already-open matching tab instead of duplicating it where consistent with existing Explore behavior;
- close the temporary pop-up;
- preserve the map tab and its current visual state.

The graph must use the same stable project, source, and entity identifiers used by existing Explore.

## 7. Search And Filters

### Search

The graph toolbar should include graph search.

Search should match existing local ontology search behavior where practical.

Search results should:

- highlight matching loaded boxes;
- allow the user to focus a result;
- offer bounded loading when a result exists locally but is not yet loaded.

The graph should not implement a second independent semantic search engine in React.

### Filters

The filter control should be collapsed by default.

Opening it may expose filters such as:

- classes;
- object properties;
- datatype properties;
- individuals;
- asserted versus inferred relationships if supported;
- source;
- loaded relationship type.

Filters affect only visualization.

They must not alter ontology data or the existing Explore outline.

The user should be able to clear all graph filters.

## 8. Graph State

### Temporary State

The following are temporary graph state:

- loaded nodes and edges;
- expanded neighborhoods;
- box positions;
- zoom;
- scroll position;
- selected entity;
- filters;
- search text;
- selected layout.

This state should survive normal interaction within the graph tab.

The specification should decide whether it survives:

- switching to another Explore tab;
- closing and reopening the graph tab;
- page reload;
- server restart.

Phase 9 does not require durable shared layout persistence.

### Ontology Refresh And Staleness

If the project ontology changes while the graph is open, Entio should not silently mix old and new graph state.

The graph should use the current project fingerprint or equivalent version marker.

When stale:

- show a clear refresh state;
- preserve the old view until the user refreshes where practical;
- reload graph data from the new project state;
- never present stale entities as current without warning.

## 9. Server And Frontend Ownership

### Kotlin Server

The server should own:

- project and source scope;
- entity identity;
- graph-neighborhood queries;
- relationship meaning;
- asserted versus inferred provenance;
- bounded loading;
- pagination or continuation;
- permissions;
- current project fingerprint;
- mapping from graph selection to Explore entity identity.

### React

React should own:

- drawing boxes and edges;
- local box positions;
- zoom and scroll interaction;
- temporary selection;
- pop-up placement;
- current graph filters;
- layout presentation;
- visual focus and highlighting.

React must not:

- parse raw ontology files;
- infer ontology relationships;
- assign semantic provenance;
- create ontology edits;
- duplicate semantic-engine behavior.

## 10. Accessibility

Phase 9 should support:

- keyboard focus for graph controls;
- visible focus states;
- Escape to close the pop-up;
- a keyboard-accessible way to select loaded entities;
- text labels that do not rely only on color;
- accessible names for zoom, fit, search, filter, and layout controls;
- readable contrast;
- reduced-motion behavior where appropriate.

The specification should define the practical keyboard navigation model for the graph itself.

## 11. Performance And Scale

Phase 9 should include deterministic fixtures for:

- a small ontology;
- a medium ontology;
- a 500-entity ontology;
- a 1,000-entity ontology.

The browser should not render every project entity by default on large fixtures.

Performance work should measure at least:

- initial graph-data response;
- initial render;
- box dragging;
- zoom and scroll responsiveness;
- bounded expansion;
- search-to-focus behavior;
- pop-up opening;
- tab handoff to Explore details.

The specification and ExecPlan should establish evidence-based thresholds on supported development hardware.

## 12. Security And Permissions

The graph may display only data the current user is allowed to browse.

The server must enforce:

- project scope;
- source scope;
- browse permission;
- cross-user isolation;
- cross-project isolation.

Entity IDs and source IDs from the browser must be treated as untrusted input.

The graph must not expose:

- secrets;
- credentials;
- filesystem paths;
- raw server errors;
- unrestricted source contents;
- entities outside the current user's allowed project scope.

## 13. Compatibility

Phase 9 must preserve:

- current Explore behavior;
- existing entity-detail tabs;
- current outline sidebar behavior;
- ontology editing flows;
- staged changes;
- proposals;
- validation;
- reasoning;
- SHACL;
- FIBO browsing;
- AI workflows;
- collaboration behavior;
- CLI behavior;
- VS Code behavior.

The graph is additive.

## 14. Suggested Delivery Areas

The specification and ExecPlan should likely divide the work into areas such as:

1. graph read contracts and bounded neighborhood service;
2. graph tab integration with Explore;
3. box and edge rendering;
4. zoom, scroll, pan, drag, and layout behavior;
5. entity selection and temporary pop-up;
6. `View Details` handoff to existing Explore tabs;
7. search, filtering, and bounded expansion;
8. staleness, limits, permissions, and error states;
9. performance, accessibility, and end-to-end tests.

The final slice structure should be decided by the ExecPlan after repository inspection.

## 15. Required Test Scenarios

The later specification and ExecPlan should include tests for at least:

- `View as map` opens or focuses one graph tab;
- existing outline sidebar is reused;
- graph boxes come from current local ontology data;
- classes, object properties, datatype properties, and individuals render distinctly;
- supported relationship types produce correct labeled edges;
- clicking every supported box type opens the read-only pop-up;
- dragging does not accidentally trigger a click;
- clicking empty space closes the pop-up;
- `View Details` opens the correct Explore entity tab;
- selecting an outline entity focuses it in the graph;
- collapsed filter opens and closes correctly;
- search focuses loaded results;
- search can load a bounded missing result;
- filters hide and restore boxes without changing ontology state;
- vertical scrolling reaches the full work area at every zoom;
- horizontal scrolling reaches the full work area at every zoom;
- pinch zoom keeps the graph point under the pointer stable;
- explicit zoom controls work;
- fit and reset work;
- graph limits produce clear user feedback;
- stale project fingerprints require refresh;
- permissions prevent cross-project access;
- no graph interaction creates ontology edits;
- all existing Explore tests continue to pass.

## 16. Acceptance Criteria

Phase 9 is complete when:

1. Explore includes a `View as map` action.
2. The action opens or focuses one graph tab for the current project.
3. The existing project outline sidebar remains available.
4. The graph uses real local ontology entities and relationships.
5. Classes, properties, and individuals are visually distinguishable.
6. Users can drag boxes and attached edges update.
7. Users can scroll horizontally and vertically to every loaded box at every zoom level.
8. Pinch zoom is anchored to the pointer or pinch center.
9. Clicking any supported entity box opens a small read-only information pop-up beside it.
10. The pop-up includes `View Details`.
11. `View Details` opens or focuses the correct existing Explore detail tab.
12. Search and collapsed filters work.
13. Large ontologies use bounded loading rather than rendering everything by default.
14. Graph state never changes ontology semantics.
15. Stale graph data is detected.
16. Permissions and project scope are enforced by the server.
17. Deterministic unit, integration, frontend, and end-to-end tests pass.
18. Existing Phase 1 through Phase 8 behavior remains unchanged.

## Open Questions For The Specification

The specification should resolve:

- What exact entities appear in the initial graph?
- What exact initial node and edge limits should be used?
- Which graph layout library, if any, is permitted by the current frontend architecture?
- Which layout modes are required in the first release?
- Should inferred relationships be included in Phase 9 or deferred?
- Should literal values ever appear as boxes?
- Should annotation properties appear in the first graph?
- Is the minimap required or optional?
- How much graph state should survive tab switching or page reload?
- Should selecting an outline entity automatically expand the graph if missing?
- What exact relationship facts belong in the temporary pop-up?
- What performance thresholds should block completion?
- What exact keyboard-navigation model is practical for loaded graph entities?

Answers must stay within this scope. If resolving an open question would introduce editing, durable collaborative layouts, AI grouping, raw graph querying, or another excluded feature, the specification must defer it rather than expanding Phase 9.
