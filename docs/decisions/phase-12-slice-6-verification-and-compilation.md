# Phase 12 Slice 6: Grounded Verification And Compilation

Status: Complete on 2026-07-31 after the explicitly authorized Slice 5/Slice 6
dependency reorder.

## Decision

Kotlin verifies every grounded candidate, evidence reference, disposition,
frozen selection, selected kind, source permission, connected reference, and
freshness fingerprint before constructing the existing `DocumentSemanticPlan`.
It never substitutes a different retrieved entity to make a provider decision
compile.

Exact full-state matches outside the prompt-visible top 20 prevent silent
duplicate creation. Missing reviewer-solvable selections, writable sources,
domains, ranges, datatypes, types, and prerequisites become explicit
`NeedsInput` fields. Imported and FIBO entities remain valid read-only reuse
targets but cannot be extension targets.

Verified connected components are passed to the existing semantic compiler.
Unsupported complex rules remain review-only; unresolved or unsafe meaning does
not become an executable operation.

## Explicit Contract Amendment

The approved grounded response did not carry the literal value or datatype
intent required by the existing compiler for datatype assertions, datatype
ranges, and supported datatype constraints. With explicit user authorization,
the provider-neutral grounded item now carries optional `literalValue` and
`datatypeIntent` fields with the same deterministic invariants as the existing
semantic-plan contract. Kotlin does not infer either value from labels.

The strict OpenAI response schema was extended by the same two neutral fields;
it still excludes final entity IRIs, RDF operations, source writes, and approval
authority.

## Verification

Focused verifier and existing compiler regression tests cover new compilation,
frozen reuse, invented and wrong-kind selections, stale ontology state,
full-state duplicates, reviewer-solvable missing roles, and imported extension
protection. The complete Slice 6 verification commands are recorded in the
slice commit after successful execution.
