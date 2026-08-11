# Phase 13 Slice 12: Quality, Performance, Security, And Documentation

Date: 2026-08-11

Status: Complete

Branch: `feature/phase-13-slice-12-quality-and-documentation`

## Goal And Outcome

This slice proves the Phase 13 acceptance criteria, records quality,
performance, security, and compatibility evidence, and updates repository
documentation only after the accumulated implementation passes verification.
It adds no product feature or write path.

Local startup now overlaps trusted ONNX runtime initialization with checksum
verification and tokenizer construction. A model session is still created only
after every model and tokenizer asset passes its pinned checksum. The approved
inference-exported model uses basic graph optimization and four bounded intra-op
workers. Repeated fresh-process acceptance runs remain below five seconds while
preserving the locked reference vector, deterministic concurrent output, and
retrieval-quality gates. The full-corpus vector asset and its checksums were
regenerated deterministically for this runtime setting.

An isolated `phase13PerformanceBenchmark` task measures the unchanged approved
thresholds outside the shared JUnit JVM. The shared JVM can close and recreate
ONNX sessions between unrelated tests, which makes it unsuitable for measuring
a true first process-local inference.

The main browser E2E fixture was also brought up to the already implemented
domain-migration response contract. This is a test-fixture correction; it does
not change production behavior.

## Files Modified

- `semantic-engine/build.gradle.kts`
- `semantic-engine/src/main/kotlin/com/entio/semantic/LocalSentenceEmbeddingService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/Phase13PerformanceBenchmark.kt`
- `external-ontologies/domain-search/fibo/master_2026Q2/search-manifest.yaml`
- `external-ontologies/domain-search/fibo/master_2026Q2/checksums/search-sha256sums.txt`
- `external-ontologies/domain-search/fibo/master_2026Q2/vectors/float32-le-v1.bin`
- `web-app/e2e/workbench.spec.ts`
- `README.md`
- `AGENTS.md`
- `docs/phase-summaries/phase-13-summary.md`
- this completion record

## Measured Results

The isolated benchmark ran on an Apple M2 with 16 GiB memory, macOS 26.5.2
arm64, and Temurin 21.0.12+8. The conservative maxima across five passing fresh
processes were:

- warm index load: 706 ms, limit 3,000 ms;
- first local inference: 4,785 ms, limit 5,000 ms;
- warm default p95: 51 ms, limit 300 ms;
- lexical/structural p95: 38 ms, limit 150 ms;
- broad-search p95: 46 ms, limit 750 ms;
- eight-request concurrent p95: 158 ms, limit 1,000 ms;
- model plus index assets: 106,563,771 bytes, limit 250 MiB;
- benchmark peak memory footprint: 98,173,816 bytes, limit 512 MiB.

Full-corpus index generation completed in 54 seconds, catalog generation in 14
seconds, catalog verification in 10 seconds, and independent search-index
regeneration and verification in 53 seconds. Each is below the approved
15-minute build-time limit.

The unchanged locked benchmark passed with recall@10 0.9167, precision@3
0.7727, kind correctness 1.0, hard-negative suppression 1.0, no-match
correctness 1.0, and stable ordering. Development and regression fixture gates
also passed.

## Security And Privacy Review

The accumulated suites verify fail-closed checksum handling for the FIBO
package, model, and index; corrupt-vector lexical degradation; project-path and
symlink confinement; cross-user, cross-project, expired, and stale server-
issued IDs; bounded adversarial input; safe error and log redaction; read-only
search routes; browser text rendering; and activation, apply, provenance, and
rollback fault recovery. No browser route exposes raw vectors, credentials, or
host-absolute filesystem paths. Retrieval has no runtime network fallback.

## Verification

The following commands passed with Temurin 21 selected only for the Gradle
processes:

```text
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:generateDomainSearchIndex
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew :semantic-engine:phase13PerformanceBenchmark
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
```

Gradle test, build, and check passed. The production web dependency audit found
zero vulnerabilities; 119 web tests, four Playwright workflows, and 39 VS Code
tests passed. The Vite build retained its existing chunk-size warning, and npm
reported two development-only audit findings during installation; neither
affects the required production dependency audit.

The slice is committed once, pushed without force, and merged locally into
`main` with a non-fast-forward merge. `main` is not pushed. There are no known
Phase 13 limitations beyond the approved scope and non-goals.
