# Phase 9 Slice 5 Completion: Dependency-Free Graph Renderer

## Status

Complete.

## Context and decision

Slice 0 approved a focused React/SVG renderer rather than a graph dependency. Phase 9 needs bounded read-only rendering, native two-axis reachability across zoom levels, accessible nodes, and temporary client layout without moving semantic meaning into React.

Entio therefore owns a small deterministic hierarchy-layout helper and React/SVG renderer under `workbench/ontology-map`. Kotlin still owns node and edge meaning. React owns only coordinates, selection, focus, visibility, zoom, scroll, pan, and temporary drag positions.

## Delivered behavior

- Asserted subclass edges produce the primary left-to-right class tree; non-hierarchy relationships never control class placement.
- Siblings are ordered deterministically by subclass, connected-property, typed-individual, and relationship counts, followed by label and IRI.
- Separate class trees are packed into deterministic layout clusters using only asserted property domain/range connectivity. Property-connected trees stay near each other, while disconnected tree clusters receive additional spacing; these are presentation clusters, not semantic groups.
- Object and datatype properties are anchored below their asserted domain class, while individuals remain hidden until explicitly revealed.
- `Focus` and `Full map` reuse the same bounded server graph without assigning new semantic meaning. The hierarchy-first tree remains the layout strategy rather than a separate user-facing mode.
- Large branches show child counts and remain collapsed after the first bounded group. Temporary positions remain stable as the bounded map changes.
- Cross-branch relationships use curved, lower-emphasis paths; selection highlights the asserted neighborhood and subdues unrelated nodes and edges.
- The compact, draggable map summary is intentionally limited to identity, provenance, an optional definition, three relationship counts, and a handoff to the full entity details view. Its constrained position is temporary map-tab state.
- Entity/source filters live once beside Project Outline search and drive outline, search-result, and map visibility from shared temporary state; edge-kind filters in that popover remain map-specific.
- Accessible HTML buttons inside SVG `foreignObject` nodes provide distinct class, object-property, datatype-property, and individual marks and styling.
- Every directed SVG edge renders its server-provided label and updates from current endpoint positions during drag.
- Movement below four CSS pixels remains a click; movement at or above the threshold drags.
- Recomputed padded world bounds and a scaled SVG surface keep horizontal and vertical content reachable from 25% through 200% zoom.
- Ordinary wheel/trackpad events retain native two-axis scrolling. Ctrl-wheel/pinch performs pointer-anchored zoom with 25%-200% clamping.
- Space-drag and middle-pointer drag pan the scroll viewport. Controls expose zoom percentage, fit, and confirmed reset.
- Arrow keys select the nearest node in the requested spatial direction with deterministic tie-breaking. A loaded-entity list provides a non-spatial focus fallback.
- Reduced-motion preferences suppress smooth reset/fit motion and CSS animation.
- Positions, zoom, and selection remain temporary per-open-map state in `ProjectWorkspace`; they are never persisted or sent to the server.

## Consequences

The renderer adds no production dependency and no semantic or editing authority. The code is intentionally bounded to Phase 9 limits rather than serving as a general graph framework. Future layouts, minimaps, durable coordinates, editing gestures, and semantic calculation remain out of scope.

## Verification

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test -- --run
npm run build
```

From the repository root:

```bash
git diff --check
```
