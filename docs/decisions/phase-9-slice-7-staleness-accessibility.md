# Phase 9 Slice 7 Completion: Staleness And Accessibility

## Status

Complete.

## Slice boundary

This slice hardens only the Phase 9 graph boundary and UI for stale fingerprints, partial failures, keyboard use, focus, contrast, and reduced motion. It adds no WebSocket protocol, broad accessibility framework, raw errors, silent graph replacement, or unrelated UI changes.

## Delivered behavior

- Every initial, neighborhood, and continuation read retains the selected-source fingerprint contract established in Slices 3 and 4.
- A changed query fingerprint or a stale expansion response marks the existing graph stale without merging old and new data.
- A modal stale overlay preserves the old display for reference, blocks current-data actions, announces the condition, and offers explicit refresh.
- Refresh retains applicable source/node/edge filters but clears positions, selection, expansion, continuation, and the old fingerprint before accepting the refreshed graph. Focus returns to the graph viewport.
- Initial and incremental failures use bounded, non-sensitive messages and keep existing map data unchanged. Abort errors do not produce an alert or retry failure state.
- Expansion, partial-limit, stale, and retry states use live regions without announcing raw server internals.
- All visible loaded nodes remain HTML buttons reachable through the graph, arrow-key navigation, and loaded-entity list. Escape closes summaries; stale refresh receives focus; graph focus and contrast remain explicit.
- Reduced-motion behavior continues to suppress graph animation and smooth movement where the user requests it.

## Manual accessibility checklist

- Tab from controls to the scrollable graph and each loaded visible entity.
- Use all four arrow keys and verify deterministic focus movement.
- Activate each node kind with Enter/Space; close its summary with Escape.
- Open the loaded-entity list and focus a named entity without spatial navigation.
- Trigger stale state, hear the alert-dialog announcement, refresh, and verify focus returns to the graph.
- At 200% browser zoom and graph zoom, confirm node labels, controls, pop-up, and stale overlay remain readable.
- With reduced motion enabled, confirm fit/reset and state changes do not animate.

## Verification

```bash
./gradlew :web-server:test
cd web-app
npm test -- --run
npm run build
```

From the repository root:

```bash
git diff --check
```
