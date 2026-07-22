# Phase 9 Slice 0: Graph Renderer Feasibility

## ExecPlan Slice Implemented

Phase 9 Slice 0, Approval and Graph-Library Feasibility Gate.

## Goal

Choose a frontend graph approach that can satisfy Entio's native two-axis scrolling, pointer-anchored zoom, full-node reachability, drag/click separation, keyboard accessibility, reduced-motion, build, security, and dependency boundaries before production implementation begins.

## Decision

Phase 9 will use a focused React/SVG renderer built with the repository's existing React stack. It will not add a graph, layout, or gesture dependency.

`@xyflow/react` 12.11.2 passed the basic ecosystem checks but was rejected for this phase's viewport model. React Flow owns a transformed viewport whose zoom does not change the browser layout size of an external scroll surface. Entio's approved interaction requires native horizontal and vertical scroll bounds to scale from 25% through 200%, while keeping the graph point under the pointer stable. Making both systems authoritative would require a second synchronization layer around the library transform and was judged a fragile override under the Slice 0 stop conditions.

The focused React/SVG approach keeps one geometry model:

- semantic nodes and edges arrive from Kotlin-owned server contracts;
- deterministic layout returns positive world coordinates;
- the SVG and containing world use actual scaled dimensions;
- native `scrollLeft` and `scrollTop` provide two-axis reachability;
- pointer anchoring is calculated from scroll position and scale;
- accessible SVG groups and a loaded-entity control provide keyboard selection;
- the four-pixel threshold separates node dragging from selection;
- reduced-motion behavior is implemented through existing CSS and React state.

The HTML prototype demonstrated this interaction shape, but its fake data, hard-coded relationships, group concepts, minimap, alternate layouts, and prototype JavaScript are not implementation inputs.

## Feasibility Evidence

The disposable proof was created outside the repository at `/tmp/entio-phase9-slice0.UB8y1X/proof` and is not a product artifact.

Package metadata:

- package: `@xyflow/react` 12.11.2;
- license: MIT;
- peer dependencies: React and React DOM 17 or newer, compatible with the repository's React 19.2.7;
- unpacked package size: 1,208,222 bytes.

Disposable proof results:

- `npm audit --omit=dev`: passed with zero vulnerabilities;
- TypeScript and Vite production build: passed;
- production output with React, React Flow, and the proof: 369.01 kB JavaScript (117.17 kB gzip) and 17.46 kB CSS (3.38 kB gzip);
- package types/source expose node ARIA labels, focusable nodes, control ARIA labels, `nodeDragThreshold`, pinch zoom, pan-on-scroll modes, and viewport hooks;
- package source treats Ctrl-wheel as pinch zoom and supports node drag thresholds;
- the proof confirmed custom node data, labeled edges, React 19 compilation, zoom bounds, focusable nodes, reduced-motion CSS, and a scroll wrapper can coexist;
- source and geometry review confirmed that the wrapper's native scroll extent remains independent of the library-owned zoom transform, so zoom-dependent reachability would require additional synchronization.

The alternative needs no second dependency and stays within the approved spec after the accompanying spec and ExecPlan amendment.

## Performance Baseline Hardware

The Slice 8 performance baseline is:

- hardware: Apple M2, 16 GiB memory;
- operating system: macOS 26.5.2 (build 25F84);
- browser: Google Chrome 150.0.7871.128;
- Java: OpenJDK 24.0.2;
- Node.js: 25.8.2;
- npm: 11.11.1.

Slice 8 must record any version drift and rerun all five warm samples on one consistently documented configuration.

## Approved Source Default

All allowed local ontology sources are selected by default, as already recorded in the approved ExecPlan. Source filters remain presentation-only.

## Files Modified

- `docs/specs/0016-phase-9-interactive-ontology-graph-visualization.md`
- `docs/execplans/0016-phase-9-interactive-ontology-graph-visualization.md`
- `docs/decisions/phase-9-slice-0-feasibility.md`

No production code, tests, dependency manifests, lockfiles, Kotlin modules, server routes, or workbench files were changed.

## Tests Added Or Updated

No repository tests were added because Slice 0 permits only a disposable feasibility proof. The proof compiled a React 19 application using `@xyflow/react`, custom graph data, labeled edges, node focusability, bounded zoom, native scroll containment, and reduced-motion styling.

## Verification Commands

- `npm view @xyflow/react version license peerDependencies dist.unpackedSize` — passed.
- `npm audit --omit=dev` in the disposable proof — passed with zero vulnerabilities.
- `npm run build` in the disposable proof — passed.
- `git diff --check` — passed before commit.
- `git status --short` — reviewed before commit; only the three allowed documentation files changed.

## Git Commit

A focused Slice 0 commit is created on `phase-9-slice-0-feasibility`; its hash is recorded by Git and in the slice handoff.

## Assumptions, Limitations, And Follow-Up

- The disposable proof is intentionally not committed.
- Real pointer, touch, keyboard, and scroll geometry tests belong to Slices 5 and 8 in Playwright; JSDOM is not accepted as sufficient evidence.
- The production renderer must not copy the prototype's fake ontology data or hard-coded layout.
- If focused React/SVG later cannot meet the approved interaction or accessibility gates, implementation stops for an approved plan amendment rather than adding another dependency.

## Notable Implementation Decisions

The feasibility decision favors one explicit world-coordinate model over combining native scrolling with a library-owned transform. This keeps viewport math testable, preserves browser scroll semantics, and avoids giving a presentation library any semantic responsibility.
