# Phase 9 Slice 8 Completion: Scale And Performance

## Status

Complete.

## Slice boundary

This slice adds deterministic test fixtures, production-build browser regression, performance harnesses, and retained evidence. It adds no production fixture endpoint, persistence, module, dependency, relaxed limit, increased timeout, or unrelated product behavior.

## Reproducible fixtures

The Kotlin fixture generator in `Phase9ScalePerformanceTest` creates asserted class chains with one local source and exactly 12, 120, 500, or 1,000 entities. Every entity after the root has one asserted direct superclass, yielding 11, 119, 499, and 999 edges respectively. The same generator and fixed IRIs/ordering run on every machine.

The browser fixture generator in `ontology-map.spec.ts` creates a bounded response of 75 nodes and 150 edges drawn from a declared 1,000-node/999-edge project. Node kinds rotate through class, object property, datatype property, and individual; edge kinds rotate through subclass, domain, range, type, and object assertion. It includes only stable IDs and asserted DTO data.

All four Kotlin fixtures prove the 75-node/150-edge initial bounds. The 1,000-entity fixture returns a continuation and never sends or renders the whole project initially. Browser expansion fixtures remain subject to the 50/100 server page and 300/600 open-tab guards.

## Baseline

- CPU: Apple M2 (`arm64`)
- Memory: 16 GiB
- OS: macOS 26.5.2, build 25F84
- Browser: Google Chrome 150.0.7871.128, headless through Playwright 1.61.1
- Java: OpenJDK 24.0.2
- Node: 25.8.2
- npm: 11.11.1
- Browser build: Vite production build served with `vite preview`; developer tools closed

## Five-run evidence

All values are milliseconds unless stated otherwise.

| Gate | Five warm runs | Median | Worst | Approved limit |
|---|---:|---:|---:|---:|
| 1,000-entity initial semantic response | 352, 254, 229, 197, 191 | 229 | 352 | 500 / 1,000 |
| 75-node/150-edge browser render | 223, 75, 70, 73, 69 | 73 | 223 | 500 / 1,000 |
| Node pop-up visual response | 49, 40, 44, 42, 46 | 44 | 49 | 100 worst |
| 75/150 deterministic layout | 0.29, 0.26, 0.26, 0.21, 0.21 | 0.26 | 0.29 | 500 / 1,000 |
| 50/100 expansion merge and layout | 1.77, 1.31, 1.26, 1.20, 1.19 | 1.26 | 1.77 | 750 / 1,500 |

The five-second production-browser interaction sample recorded 60, 60, 60, 60, and 60 rendered frames in five consecutive one-second warm windows. The worst observed long task was 0.0 ms, satisfying at least 50 FPS and no long task over 100 ms. Existing synchronous tab focus and immediate loading-state tests continue to cover detail handoff.

## Browser and read-only evidence

Playwright runs against the production build and verifies bounded entry/focus, all node and edge kinds, accessible names, keyboard movement, pop-up content, no edit control, filters, zoom/fit, stale overlay, preserved old display, explicit refresh focus, and that every graph request uses GET. The existing full workbench browser regression uses current accessible landmarks for settings and proposal review and remains green.

## Reproducible commands

```bash
./gradlew :semantic-engine:test --tests com.entio.semantic.Phase9ScalePerformanceTest --info
cd web-app
npx vitest run src/workbench/ontology-map/performance.test.ts --disableConsoleIntercept
npx playwright test --config playwright.performance.config.ts e2e/ontology-map.spec.ts
```

Full slice verification:

```bash
./gradlew :core-types:test :semantic-engine:test :web-server:test
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```
