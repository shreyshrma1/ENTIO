# Phase 9 Slice 6 Completion: Map Inspection And Expansion

## Status

Complete.

## Slice boundary

This slice completes read-only map inspection, filtering, existing-navigation handoff, and bounded expansion. It does not add editing, staging, proposals, a permanent details pane, raw RDF, client semantic search/ranking, persistence, arbitrary removal, or unbounded merging.

## Delivered behavior

- Pointer click, Enter/Space activation, loaded-entity selection, and spatial keyboard focus select supported node kinds and open the same bounded summary pop-up.
- The viewport-contained pop-up displays only server-provided label, kind, definition excerpt, relationship counts, and asserted provenance. Escape or its close control dismisses it; it contains no edit action.
- `View Details` delegates to the existing entity-tab flow. Duplicate entity tabs retain the established focus behavior.
- While the map is active, an outline/search selection focuses an already-loaded supported local entity. An unloaded supported local entity requests confirmation after manual movement or expansion, then replaces the temporary map with a centered server read. External or unsupported results keep normal detail navigation.
- Collapsed filters cover the selected local source, four node kinds, and five asserted edge kinds. Hidden endpoints also hide their edges; Clear restores all loaded data without deleting it.
- Four explicit expansion categories call the fingerprint-bound neighborhood API. Server continuations remain opaque and are consumed through `Load more`.
- Pages merge by stable node/edge IDs, preserve unaffected positions, reject orphan edges, and stop at 300 nodes or 600 edges with an explicit partial-state message.
- Expansion failures preserve the current graph and expose a safe retryable status. No map interaction calls mutation, staging, or proposal APIs.

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
