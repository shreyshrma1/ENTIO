# Phase 4 Slice 11: Machine-Readable CLI Boundary

## ExecPlan Slice Implemented

Phase 4, Slice 11: Machine-Readable CLI Boundary.

## Goal

Expose the Phase 4 reasoning, SHACL, and proposal-impact capabilities through a thin, backward-compatible CLI boundary with deterministic JSON responses and machine-readable failures.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/EntioCli.kt`
- `cli/src/main/kotlin/com/entio/cli/Phase4Commands.kt`
- `cli/src/test/kotlin/com/entio/cli/Phase4CliBoundaryTest.kt`
- This completion record.

No build files or dependencies were changed. The CLI delegates project loading, reasoning, SHACL validation, shape authoring descriptors, graph preview, and proposal-impact analysis to existing semantic-engine and graph-diff services.

## Commands Added

- `reasoning-refresh` with the `reasoning` alias.
- `reasoning-explain`.
- `shacl-validate` with explicit `asserted-only` and `asserted-and-inferred` modes.
- `shacl-shapes`.
- `proposal-impact`.

Responses contain Entio-owned JSON fields for status, fingerprints, completeness, asserted/inferred facts, SHACL findings, validation errors, explicit graph diff, reasoning impact, and SHACL impact. Existing CLI commands remain registered unchanged.

## Tests Added

`Phase4CliBoundaryTest` verifies:

- Stable reasoning metadata and fingerprints.
- SHACL validation mode and violation output.
- SHACL shape descriptor output.
- Structured invalid-mode errors on the configured CLI output stream.
- Deterministic proposal-impact output with separate explicit, reasoning, and SHACL sections.

The fixture uses a named SHACL property shape so repeated loads preserve RDF identity and deterministic result fingerprints.

## Verification

- `./gradlew :cli:compileKotlin --no-daemon --console=plain` — passed.
- `./gradlew :cli:test --rerun-tasks --no-daemon --console=plain` — passed; 53 tests completed.

## Git Commit

This completion record is included in the focused Slice 11 Git commit.

## Assumptions And Limitations

- The CLI remains a routing and serialization boundary; semantic policy and source mutation remain in the existing Kotlin modules.
- Proposal-impact currently evaluates the configured project graphs and returns the available Entio impact contracts. It does not add a second proposal lifecycle or persistence layer.
- The CLI exposes SHACL shape descriptors for the supported shape subset; unsupported shape constructs continue to be reported by the semantic engine.
- Full Phase 4 integration with the VS Code workbench is reserved for Slice 12.

## Implementation Decisions

- Picocli annotation aliases are used without duplicate explicit registration.
- Failure responses are written through each command's configured Picocli output writer so embedded callers and tests receive the same machine-readable result as command-line users.
- JSON serialization uses the existing CLI JSON boundary and Entio-owned contracts; third-party OWL API, HermiT, and Jena types do not cross the CLI boundary.
