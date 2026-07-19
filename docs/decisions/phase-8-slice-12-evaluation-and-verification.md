# Phase 8 Slice 12: Evaluation And Verification

## Goal

Prove the completed Phase 8 workflow with permanent deterministic evaluation, large-ontology boundedness, security, performance, and full repository regression coverage.

## Permanent Evaluation Coverage

`AiPhase8EvaluationTest` records deterministic outcomes for small editing, explanation, medium lending, a 50-edit domain task, hierarchy refactoring, SHACL repair, FIBO reuse, stale collaboration, unavailable-model recovery, and adversarial safety attempts. Existing focused suites remain the executable evidence for the corresponding task lifecycle, retrieval, planning, batching, analysis, repair, review, web-contract, and browser journeys.

The generated 500- and 1,000-entity cases assert that expanded task context contains at most 50 entities and 64,000 approximate bytes, remains deterministically ordered, omits a known source-tail marker, and never substitutes complete project content for bounded retrieval. The existing batch regression prepares 50 typed edits through bounded atomic batches.

`AiPhase8SecurityEvaluationTest` verifies that untrusted project text remains delimited data, cannot widen the server capability registry, and cannot obtain shell, file, network, secret, reviewer, application, raw RDF, or SPARQL authority. The full existing security suite supplies cross-user/project, replay, stale-data, malformed-input, redaction, and limit-bypass coverage.

## Benchmark Method And Result

- Operating system: macOS Darwin 25.5.0.
- Processor architecture: arm64.
- Available memory: 17,179,869,184 bytes (16 GiB).
- JVM: OpenJDK 24.0.2, runtime build 24.0.2+12-54.
- Node: v25.8.2.
- Workload: build expanded task context from deterministic generated project snapshots without a provider, credential, or network.
- Warm-up: two unmeasured builds for each fixture size.
- Measured runs: seven per fixture size in one test invocation.
- 500 entities: 0.247875 ms median, 0.264208 ms maximum.
- 1,000 entities: 0.470334 ms median, 0.478958 ms maximum; diagnostic only.
- Blocking 500-entity threshold: 500 ms maximum.

The threshold intentionally leaves substantial development-machine variance while still detecting accidental whole-ontology serialization or an order-of-magnitude retrieval regression. Correctness, bounds, ordering, and leakage assertions remain blocking at both sizes.

## Final Elapsed-Time Policy

The final defaults are five minutes of active execution per package and thirty minutes per task. Generated context preparation is sub-millisecond on the recorded environment, so it does not justify consuming the package budget. The five-minute package limit also leaves bounded headroom beyond the existing two-minute ceiling for one provider request, deterministic preparation, and analysis. The thirty-minute task limit permits multiple serial packages while the independent limits of 20 edits per batch, 100 edits, 200 tool calls, eight repair cycles, one mutating provider request at a time, and bounded context remain authoritative. Fake-clock lifecycle tests verify limit transitions without wall-clock waiting. These values are development safeguards, not service guarantees.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskContracts.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiPhase8EvaluationTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiPhase8SecurityEvaluationTest.kt`
- `README.md`
- `AGENTS.md`
- `docs/architecture/ai-subsystem-map.md`
- `docs/phase-summaries/phase-8-summary.md`
- this completion artifact

## Verification

The focused evaluation, boundedness, security, and 50-edit batch tests passed. Full Kotlin test/build/check, React unit/build/Playwright, VS Code extension, and diff checks passed without a real provider credential or external model request. The npm install reported two high-severity transitive audit findings in the web application dependency tree; the approved test and build gates nevertheless passed, and no dependency was changed in this verification-only slice.
