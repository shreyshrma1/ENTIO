# Phase 10.5 Contract Repair: Graph Edge Provenance

Status: Complete
Date: 2026-07-23

Slice 5 found that the web graph edge contract exposed `Inferred` provenance but omitted the authoritative applied/proposal graph state already present in the Kotlin core edge.

The additive nullable `inferredGraphState` field now carries that existing value through Ktor. Asserted edges remain backward compatible with a null state. This prevents React from guessing whether an inferred edge belongs to the applied graph or the current proposal.

Verification:

- `./gradlew :web-server:test --tests com.entio.web.OntologyGraphWebContractTest`
- `./gradlew :web-server:build`
- `git diff --check`
