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

## Exact-Reuse Consolidation Correction

End-to-end review exposed duplicate-looking cards when several grounded items
selected the same ontology entity. Kotlin now consolidates only unconnected
`ReuseExisting` items whose complete normalized candidate meaning, grounded
label, entity kind, canonical IRI, scope, and source are identical. Evidence and
candidate provenance are unioned deterministically and the lowest confidence
dimension is retained.

Qualified candidates remain separate when their normalized meaning differs,
even if a provider incorrectly gives them the broader entity's label and selects
the same canonical IRI. This prevents consolidation from erasing qualifiers such
as `loan`, `corporate`, `legal`, or `commercial`.

The correction added two focused verifier tests and passed the approved Slice 6
verifier, compiler, plan-verifier, coverage, translator, analysis-service, and
orchestration suites. Both `:semantic-engine:build` and `:web-server:build`
passed. The web-server build used a temporary non-synced Gradle output directory
because the Desktop sync layer otherwise duplicated generated `.class` files
with a ` 2` suffix; no tracked source or test fixture was changed by that
environmental workaround.

Git status: committed as one focused correction on
`fix/phase-12-exact-reuse-consolidation` and authorized for its remote branch
only.
