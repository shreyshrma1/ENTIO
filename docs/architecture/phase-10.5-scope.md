# Phase 10.5 Scope

## Phase Name

**Phase 10.5: Inferred Facts in Explore and Ontology Map**

## Status

Draft

## Purpose

Phase 10.5 makes Entio's inferred facts visible directly in Explore.

Users should be able to turn on an `Inferred facts` filter. When enabled:

1. inferred facts appear throughout the existing Project Outline and entity details;
2. inferred relationships appear in the ontology map as clearly marked inferred edges.

This phase is read-only. It does not stage, apply, or materialize inferred facts.

## Central Product Promise

> A user can view asserted and inferred ontology knowledge together while always being able to tell which facts are stored and which were derived by reasoning.

## Problem

Entio already computes inferred relationships through its OWL reasoning system, but those results are mainly visible in Reasoning.

That makes it harder to understand reasoning while browsing:

- classes;
- properties;
- individuals;
- entity details;
- the ontology map.

Users should not need to leave Explore to see how reasoning changes their understanding of the ontology.

## Goals

Phase 10.5 should:

- add an `Inferred facts` filter to the existing Project Outline controls;
- keep inferred facts hidden by default;
- show inferred facts in the correct existing fields when enabled;
- use a light-blue visual treatment for inferred entries;
- clearly label inferred facts so color is not the only signal;
- show inferred relationships in the ontology map;
- use distinct inferred edge styling;
- keep asserted and inferred facts separate in contracts and UI state;
- reuse existing reasoning results and semantic services;
- detect stale reasoning results;
- remain fully read-only;
- preserve existing Explore, map, reasoning, staging, and proposal behavior.

## Non-Goals

Phase 10.5 must not add:

- automatic materialization of inferred facts;
- staging or applying facts from the filter;
- ontology source writes;
- editing inferred facts;
- new reasoning rules;
- a new reasoner;
- AI-generated relationships;
- inferred facts presented as asserted facts;
- inferred literal nodes in the map;
- inferred SHACL findings as ontology relationships;
- CLI or VS Code inferred-visualization changes;
- changes to the normal approval workflow.

## Supported Inferred Facts

Phase 10.5 should initially display inferred facts already supported by Entio reasoning:

1. **Class relationships**
   - inferred superclass relationships;
   - inferred subclass relationships where needed for display.

2. **Individual types**
   - inferred class membership.

3. **Object-property assertions**
   - inferred relationships between individuals.

Only complete, current, applied-graph reasoning results are eligible.

Unsupported reasoning outputs remain in the Reasoning section only.

## Default Behavior

The `Inferred facts` filter is off by default.

With the filter off:

- Explore behaves exactly as it does today;
- only asserted ontology facts appear;
- the ontology map shows asserted edges only.

With the filter on:

- inferred facts are added to the current Explore view;
- inferred map edges are added to the loaded graph;
- asserted facts remain visible;
- duplicates are not shown twice.

## Project Outline Integration

Add an `Inferred facts` control to the existing Project Outline filter area.

The same control should affect all Explore surfaces that reuse the Project Outline, including the ontology map.

The filter should not create a second outline or separate reasoning sidebar.

### Filter States

The control should support:

- off;
- on;
- unavailable;
- stale;
- loading;
- failed.

If no current reasoning result exists, the UI should explain that reasoning must be run first.

The later spec should decide whether the user may start reasoning from this state or must navigate to Reasoning.

## Where Inferred Facts Appear

When enabled, inferred facts should appear in the same fields where equivalent asserted facts already appear.

### Classes

Show inferred facts in fields such as:

- superclasses;
- subclasses;
- hierarchy entries;
- existing class relationship summaries.

### Properties

Show inferred facts in fields such as:

- inferred property usage;
- inferred object-property assertions;
- related subjects and objects where those fields already exist.

Phase 10.5 does not invent inferred domains, ranges, or property characteristics unless the existing reasoner already returns them in a supported deterministic form and the spec explicitly includes them.

### Individuals

Show inferred facts in fields such as:

- inferred types;
- inferred object-property relationships;
- incoming and outgoing inferred assertions.

### Outline Rows

If inference causes an entity to appear in an additional hierarchy location, Entio may show that placement as a light-blue inferred entry.

It must not move or replace the entity's asserted placement.

The spec should define how inferred multi-parent class placements appear without making the tree confusing.

## Visual Treatment

Inferred entries should use a light-blue treatment consistent with Entio.

Each inferred entry must include more than color, such as:

- an `Inferred` label;
- an inference icon;
- accessible text;
- a tooltip or metadata line.

Asserted facts keep their current appearance.

When the same fact is both asserted and inferred, show one asserted entry rather than two copies.

## Fact Details

Selecting an inferred entry should make its origin clear.

The existing detail view or temporary pop-up should show:

- `Origin: Inferred`;
- reasoning result reference;
- graph fingerprint or freshness state;
- inference type.

Asserted facts must remain separately identified.

A `View reasoning explanation` action may be included only if it reuses the existing reasoning explanation flow.

## Ontology Map Integration

When `Inferred facts` is enabled, the ontology map should add inferred relationships to the currently loaded graph.

### Supported Inferred Map Edges

At minimum:

- inferred subclass or superclass edges;
- inferred individual-type edges;
- inferred object-property assertion edges.

### Edge Styling

Inferred edges must be clearly different from asserted edges.

Recommended treatment:

- light-blue line;
- dashed stroke;
- `Inferred` edge label or badge;
- lower emphasis than asserted edges;
- clear hover and focus state.

Color must not be the only distinction.

The map legend should include:

- asserted relationship;
- inferred relationship.

### Duplicate Handling

If the same relationship is already asserted:

- render the asserted edge only;
- do not render a second inferred edge;
- details may indicate that the relationship is also entailed.

### Layout Behavior

Inferred edges should not control the main hierarchy layout.

The map should remain hierarchy-first:

- asserted class hierarchy determines primary placement;
- inferred edges are layered on top;
- inferred cross-links should not reorganize the whole map;
- enabling or disabling inferred facts should not cause unnecessary node movement.

Inferred-only nodes may be added only when required to display a supported inferred edge and only within existing graph limits.

## Data And Freshness Rules

Inferred facts must come from a complete applied-graph reasoning result.

The server must verify:

- project ID;
- source scope;
- reasoning status;
- graph fingerprint;
- result completeness;
- current user permissions.

If the ontology changes after reasoning:

- inferred facts become stale;
- the filter must not present them as current;
- asserted data remains visible;
- inferred entries and edges are hidden or clearly blocked until reasoning is refreshed.

Recommended default: hide stale inferred facts and show a clear `Reasoning results are stale` message.

## Server And Frontend Ownership

Kotlin should own:

- supported inferred fact extraction;
- asserted/inferred separation;
- duplicate suppression;
- canonical fact identity;
- source and project scope;
- reasoning freshness;
- bounded result limits;
- mapping inferred facts into existing Explore read models;
- mapping inferred relationships into graph contracts.

React should own:

- filter state;
- light-blue presentation;
- legend display;
- loading and stale messages;
- showing or hiding server-provided inferred entries and edges.

React must not infer ontology relationships independently.

## Read Contracts

The later spec should decide whether to:

1. extend existing Explore and graph DTOs with optional inferred facts; or
2. add a focused inferred overlay response.

Recommended approach:

> Extend existing read contracts with optional provenance-aware facts so server ordering, deduplication, freshness, and limits remain authoritative.

Each fact should include:

- stable identity;
- subject;
- predicate or relationship kind;
- object or target;
- asserted or inferred origin;
- reasoning result reference;
- graph fingerprint;
- source identity where relevant;
- current or stale state.

## Limits And Performance

Inferred facts must respect existing Explore and Phase 9 map limits.

The spec should define:

- maximum inferred facts per entity;
- maximum inferred edges per map response;
- aggregate map limits when asserted and inferred edges are combined;
- pagination or `load more` behavior;
- behavior when the reasoning result exceeds visible limits.

Enabling inferred facts must not load the entire reasoning result into the browser.

Required fixtures should include:

- small ontology;
- medium ontology;
- 500-entity ontology;
- 1,000-entity ontology;
- dense inferred-relationship fixture.

## Permissions And Safety

Users may see inferred facts only for projects and sources they are allowed to browse.

The server must enforce:

- user identity;
- project scope;
- source scope;
- browse permission;
- reasoning-result access;
- cross-user and cross-project isolation.

Phase 10.5 must not expose:

- secrets;
- server paths;
- raw source content;
- unrestricted reasoning internals;
- facts from another project;
- imported or external data beyond the user's existing read scope.

## Accessibility

The inferred display should support:

- visible text labels in addition to color;
- screen-reader descriptions;
- keyboard access to inferred entries;
- keyboard access to inferred map edges if edges are interactive;
- clear legend wording;
- sufficient contrast;
- reduced-motion behavior;
- understandable stale and unavailable states.

## Suggested Delivery Areas

The later spec and ExecPlan should likely separate work into:

1. provenance-aware inferred read contracts;
2. reasoning overlay service and freshness checks;
3. Explore entity-detail and outline integration;
4. Project Outline filter behavior;
5. ontology map inferred-edge integration;
6. visual treatment, legend, and accessibility;
7. scale, staleness, and end-to-end regression tests.

The exact slices should be decided after repository inspection.

## Required Test Scenarios

The spec and ExecPlan should include tests for:

- inferred filter off by default;
- enabling the filter with a current reasoning result;
- no reasoning result available;
- stale reasoning result;
- failed or incomplete reasoning result;
- inferred superclass shown under classes;
- inferred individual type shown under individuals;
- inferred object-property assertion shown under properties and individuals;
- light-blue inferred entry styling;
- non-color `Inferred` labeling;
- asserted and inferred duplicate suppression;
- asserted fact remains primary when both asserted and inferred;
- inferred map edge styling and legend;
- inferred edges do not control primary tree layout;
- enabling and disabling inferred facts preserves manual map positions;
- graph limits include asserted and inferred edges;
- inferred-only node loading remains bounded;
- cross-project and cross-user rejection;
- no staging, proposal, apply, or source-write call from the filter;
- existing Explore, Reasoning, map, staging, proposal, CLI, and VS Code regressions.

## Acceptance Criteria

Phase 10.5 is complete when:

1. Project Outline includes an `Inferred facts` filter.
2. The filter is off by default.
3. Enabling it shows supported current inferred facts throughout Explore.
4. Inferred facts appear in the same fields as equivalent asserted facts.
5. Inferred entries are light blue and explicitly labeled `Inferred`.
6. Asserted and inferred duplicates are shown once as asserted.
7. The ontology map shows clearly marked inferred edges.
8. Inferred edges do not control the primary hierarchy layout.
9. Stale or incomplete reasoning results are not shown as current.
10. All inferred data remains read-only.
11. No ontology source, staging, or proposal state changes through this feature.
12. Kotlin remains authoritative for ontology meaning and provenance.
13. Existing Explore and map behavior remains unchanged when the filter is off.
14. Deterministic unit, integration, frontend, accessibility, scale, and end-to-end tests pass.

## Open Questions For The Spec

The spec should resolve:

- Should stale inferred facts be hidden immediately or shown under a stale overlay?
- Should the filter be one global Explore setting or stored separately per tab?
- Should enabling the filter automatically run reasoning when no current result exists?
- How should inferred multi-parent class placements appear in the outline tree?
- Should inferred property domains or ranges be included if available?
- Should inferred map edges be interactive?
- Should users be able to open the existing reasoning explanation from an inferred entry?
- What exact per-entity and per-map inferred limits should apply?
- Should inferred-only related entities load automatically or only on demand?
- Should the filter state survive tab switching, page reload, or project changes?

If an answer requires materialization, editing, new inference rules, AI-generated facts, or direct source mutation, it must be deferred rather than expanding Phase 10.5.
