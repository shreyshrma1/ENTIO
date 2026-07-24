# Phase 11 Correction: React Router Security Advisory

## Status

Complete and verified.

## Reason For The Correction

The final Phase 11 vulnerability gate identified `GHSA-qwww-vcr4-c8h2` in React Router 7.18.1. Although Entio does not use the affected unstable React Server Components APIs, npm's required audit has no per-advisory exception mechanism and therefore could not pass with React Router 7.

A proposed downgrade to 7.11.0 was rejected before commit because it restored several older advisories. React Router 8.3.0 is the first available version that clears both advisory sets.

## Delivered

- Replaced the retired `react-router-dom` package with the consolidated `react-router` 8.3.0 package.
- Updated the three browser-router imports to use the consolidated package.
- Kept Entio on `BrowserRouter`; no server-component or server-action APIs were introduced.
- Preserved the existing project routes, links, navigation hooks, and search-parameter behavior.

## Verification

- `npm audit --omit=dev` — passed with zero vulnerabilities.
- `npm test` — passed, 23 files and 95 tests.
- `npm run build` — passed, including TypeScript verification.
- `git diff --check` — passed.

The existing Playwright suite was also run. Its failures were reproduced unchanged on the accumulated pre-upgrade `main`: stale reasoning-role assertions and existing screenshot baselines. Those test corrections remain part of the approved Slice 8 verification work and are not included in this dependency correction.

## Boundaries

- No application behavior, route structure, server code, or Phase 11 product scope changed.
- No React Router RSC, server-action, or framework-mode API is enabled.
