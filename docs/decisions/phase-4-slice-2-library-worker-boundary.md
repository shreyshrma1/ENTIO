# Phase 4 Slice 2: Library Compatibility And Reasoning Worker Boundary

## ExecPlan Slice Implemented

Slice 2: Library Compatibility And Reasoning Worker Boundary from `docs/execplans/0007-phase-4-owl-reasoning-shacl.md`.

## Goal

Pin and verify the OWL API/HermiT and Apache Jena SHACL dependency boundary, and define a narrow versioned protocol for a future reasoning worker process.

## Files Modified

- `semantic-engine/build.gradle.kts`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ReasoningWorkerProtocol.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ReasoningWorkerProtocolTest.kt`
- `docs/decisions/phase-4-slice-2-library-worker-boundary.md`

## Dependency Decision

- Apache Jena `5.3.0` remains the RDF/SHACL library boundary.
- OWL API distribution is pinned to `5.1.9`.
- HermiT is pinned to `1.4.5.519` and resolves against OWL API `5.1.9`.
- No server, RPC, coroutine, database, persistence, or plugin dependency was added.

## Tests Added Or Updated

- Round-trip versioned worker requests and normalized responses.
- Preserve graph, import-closure, and reasoner-configuration fingerprints.
- Preserve normalized fact origin values.
- Reject protocol mismatches, malformed output, missing completed output, and invalid fields.
- Preserve structured startup/crash/timeout/cancellation/malformed-output failure kinds.

## Verification

| Command | Result |
| --- | --- |
| `./gradlew :semantic-engine:test` | Passed |
| `./gradlew dependencies` | Passed |
| `./gradlew test` | Passed |
| `./gradlew :semantic-engine:dependencies --configuration runtimeClasspath` | Passed; verified pinned OWL API/HermiT/Jena versions |

## Result

`semantic-engine` now owns a versioned, standard-library-encoded worker protocol with Entio-owned request, response, normalized-output, and failure contracts. This slice does not launch the worker, load projects, resolve imports, classify OWL ontologies, or execute SHACL validation.

## Assumptions And Limitations

- The selected OWL API/HermiT pair resolved successfully on the existing JVM/Gradle toolchain, but later reasoning behavior still requires focused compatibility tests.
- The line protocol is intentionally narrow and is not a general-purpose RPC layer.
- Worker process launch, timeout, cancellation, and result lifecycle remain reserved for later slices.

## Git

- Commit: created for this slice; see Git history for the commit hash.
- Remote push: the slice branch is pushed after verification.
