# Phase 10.5 Slice 4 Completion: Explore Inferred UI

Status: Complete
Date: 2026-07-23

## Delivered

- Added `Show inferred for applied` and `Show inferred for proposal` to the existing Project Outline filter.
- Kept both controls temporary, off by default, shared across same-project Explore tabs, resettable, and cleared when the project changes.
- Included both flags in React Query keys and Ktor request parameters without submitting semantic jobs from React.
- Added light-blue inferred presentation with explicit `Inferred · Applied` or `Inferred · Proposal` text.
- Added inferred type placement to the existing grouped object outline.
- Added bounded inferred hierarchy requests and entity-detail facts, including effective domain/range labels supplied by the server fact kind.
- Preserved asserted entries and styling when inferred equivalents are absent from the server overlay.
- Added safe updating, unavailable, and failed-state messaging while asserted content remains usable.

## Verification

- `npm ci`
- `npm test -- --run src/web/contracts.test.ts src/web/projectApi.test.ts src/workbench/EntityDetails.test.tsx`
- `npm test`
- `npm run build`
- `git diff --check`
