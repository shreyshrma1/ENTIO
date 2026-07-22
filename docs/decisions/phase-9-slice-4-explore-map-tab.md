# Phase 9 Slice 4 Completion: Explore Map Tab

## Status

Complete.

## Slice boundary

This slice adds browser graph contracts, abortable reads and query hooks, a single temporary Explore map-tab lifecycle, and a loading/empty/error shell. It preserves the existing outline and entity-detail data ownership. It does not add a rendering dependency, graph layout, gestures, semantic inference, editing callbacks, persistence, or duplicate outline queries.

## Delivered behavior

- TypeScript graph contracts match the versioned Ktor response and reject malformed node/edge references before they reach the workbench.
- Initial and neighborhood reads URL-encode project, source, entity, fingerprint, continuation, and category inputs; accept an `AbortSignal`; and never expose semantic cursor offsets.
- TanStack Query hooks pass query cancellation signals through and retry an initial transient failure once.
- Explore has one explicit map tab alongside existing entity tabs. Repeated `View as map` actions focus it instead of duplicating it.
- The selected supported local entity seeds the initial map read; without one, the server returns its bounded root overview.
- The existing project outline remains mounted and unchanged while the map shell reports loading, empty, error, retry, and loaded states.
- `View Details` delegates to the existing `openEntity` path. Map selection state is owned by `ProjectWorkspace`, so entity-detail navigation and tab switching do not discard it.
- Closing the map or changing project clears temporary state. No browser storage is used.

## Verification

```bash
cd web-app
npm test -- --run
npm run build
```

From the repository root:

```bash
git diff --check
```
