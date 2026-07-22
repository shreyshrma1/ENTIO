# Phase 9 Implementation Summary

## Status

Complete. Phase 9 delivers the approved bounded, read-only interactive ontology map through all ten ordered ExecPlan slices.

## Delivered Behavior

- Explore opens one project-scoped `Ontology Map` tab and focuses an existing map tab without needless reload.
- Kotlin constructs deterministic local asserted graph pages for classes, object properties, datatype properties, and individuals. It emits asserted subclass, domain, range, type, and object-property assertion edges without exposing semantic-library types.
- Ktor exposes authorized GET-only initial and neighborhood routes with source/entity validation, opaque in-memory continuations, graph fingerprints, stale detection, and safe error contracts.
- React owns deterministic layout and temporary tab state. Users can select and drag nodes, pan or scroll in two axes, zoom from 25% to 200%, fit/reset, search, filter, expand neighborhoods, inspect a read-only summary, and hand off to existing entity details.
- Keyboard navigation, accessible node names and edge descriptions, focus management, contrast, reduced motion, loading/error/empty/limit states, and stale-refresh recovery are covered by component and browser tests.
- No graph action stages, proposes, applies, or otherwise writes ontology data.

## Bounds And Identity

- Initial response: 75 nodes and 150 edges maximum.
- Expansion response: 50 new nodes and 100 new edges maximum.
- Open map tab: 300 nodes and 600 edges maximum.
- Search results: 20 maximum.
- Node identity is `(sourceId, entityIri)`. Same-source targets win; ambiguous cross-source targets are omitted and counted.
- Continuations are opaque, user/project/source/query/fingerprint scoped, expire in memory, and are cleared on restart.

## Architecture And Dependency Decision

`core-types` defines neutral graph contracts. `semantic-engine` owns RDF interpretation, graph assembly, ordering, fingerprints, and paging. `web-server` adapts those services to versioned authorized read contracts. `web-app` owns drawing, geometry, viewport behavior, and ephemeral interaction state. The CLI and VS Code extension did not gain graph commands.

Slice 0 rejected a graph-library dependency and selected focused React/SVG. This keeps native two-axis scroll bounds and zoom in one explicit world-coordinate model and added no graph, layout, or gesture dependency.

Primary implementation areas are `core-types`, `semantic-engine`, `web-server`, and `web-app/src/workbench/ontology-map/`. Slice records are retained at `docs/decisions/phase-9-slice-0-feasibility.md` through `docs/decisions/phase-9-slice-9-phase-completion.md`.

## Fixtures And Performance Evidence

Deterministic Kotlin fixtures cover 12, 120, 500, and 1,000 entities. Browser fixtures exercise 75 nodes and 150 edges from a declared large project, every supported node and edge kind, bounded expansion, staleness, accessibility, and GET-only read behavior.

On an Apple M2 with 16 GiB memory, macOS 26.5.2, Chrome 150, Java 24.0.2, and Node 25.8.2, retained five-run evidence recorded:

- 1,000-entity semantic response: 229 ms median, 352 ms worst;
- 75-node/150-edge browser render: 73 ms median, 223 ms worst;
- node pop-up: 49 ms worst;
- deterministic layout: 0.26 ms median, 0.29 ms worst;
- expansion merge/layout: 1.26 ms median, 1.77 ms worst;
- interaction: 60 FPS in all five warm windows, with no long task over 100 ms.

The detailed baseline and reproducible commands are in `docs/decisions/phase-9-slice-8-scale-performance.md`.

## Verification

Every slice passed its focused checks before commit, remote branch push, and clean local merge. Phase completion ran:

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd web-app && npx playwright test --config playwright.performance.config.ts e2e/ontology-map.spec.ts)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

All checks passed. The web production dependency audit reported zero vulnerabilities.

## Known Limitations

- The map is asserted-only and local-project-only. It excludes literals, annotation properties, SHACL shapes, inferred-only entities, imports, and external catalogs such as FIBO.
- It is not an editing surface and does not inspect or edit edges.
- Layout, selection, filters, positions, and loaded neighborhoods are temporary and are not persisted or shared.
- Pagination and authorization use the existing in-memory development server boundary, not production tenancy or durable continuation storage.
- The renderer is deliberately bounded and is not a full-project diagram, graph export tool, arbitrary SPARQL surface, or Protégé replacement.

## Rollback

The feature is additive and has no data migration. A rollback can first remove or hide `View as map` and the graph tab, then remove frontend graph queries/components, Ktor GET routes, semantic graph services, and finally neutral core contracts after consumers are gone. No ontology, proposal, collaboration, credential, or database state requires migration or recovery. Historical completion evidence should remain unless the implementation itself is reverted.
