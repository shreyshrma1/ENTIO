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

## Exact-Identity Review Quality Correction

Live trials showed that a provider could select a broader retrieved class and
rename a qualified candidate to that class even though the complete retrieval
state did not identify an exact match. That produced duplicate-looking cards
such as `Account` for both `Account` and `Commercial Account`, and incorrectly
presented the qualified meaning as already matched.

Kotlin now accepts `ReuseExisting` only when every candidate represented by the
grounded item has a complete-state exact-identity match to the selected
canonical IRI, scope, and source, or exactly matches a server-issued preferred
or alternate identity label. A non-exact reuse is converted to actionable
`NeedsInput`, keeps the most specific evidence-backed candidate label, exposes
the compatible server-issued selection as a reviewer option, and cannot create
an alignment implicitly. Exact unconnected reuse items consolidate by their
server-proven canonical identity, so harmless casing and alias differences no
longer create duplicate review cards.

This provider gate does not take authority away from the reviewer. The review
workspace explicitly identifies reuse items created by a reviewer resolution;
the verifier accepts only those named items after revalidating their
server-issued selection, kind, source, and fingerprints. Provider output cannot
set that authorization.

Focused verifier regression coverage proves both outcomes: exact repeated
identities consolidate with their evidence intact, while `Loan agreement`
cannot silently become a second `Agreement` match. The correction is isolated
on `fix/phase-12-grounded-review-quality` for independent verification, commit,
push, and local non-fast-forward merge.

## Candidate Relevance Correction

The next controlled two-PDF trial reached review but exposed one exact duplicate
unresolved card and several standalone relationship cards. Kotlin now
consolidates unconnected `Unresolved` items only when they have the same
supported kind, one identical normalized candidate meaning, and a label that
exactly preserves that meaning. Candidate and evidence provenance are unioned;
potentially different meanings remain separate.

A relationship-phrase candidate returned as an object property must participate
in a connected semantic component as a referenced item or through explicit
references. If it has neither, Kotlin retains it as document-only evidence
instead of asking the reviewer to invent missing subject and object semantics.
This is category- and structure-based; it does not hard-code benchmark words or
discard connected relationships.

Focused verifier tests cover both rules on
`fix/phase-12-grounded-candidate-relevance`. The correction preserves the
complete coverage ledger and does not add a candidate, recommendation, or edit
ceiling.

## Qualified-Class Resolution Correction

Verifier normalization is now returned as part of the verified result and is
the analysis installed into review. This ensures a provider's rejected broader
reuse is actually presented and processed as `Unresolved`; the review workspace
no longer retains the provider's stale `ReuseExisting` disposition behind a
`NeedsInput` card.

When a rejected broader class selection is also an exact lexical head of the
qualified candidate (for example, `Account` in `Commercial Account`), Kotlin
exposes that same server-issued selection as a suggested superclass. It does
not infer or compile the relationship automatically. Unrelated non-exact
choices do not become superclass suggestions, and provider output cannot
authorize the resulting edit.

The focused verifier regression proves that `Loan agreement` remains
unresolved while `Agreement` is offered as its reviewer-controlled superclass.
The verified normalized analysis, not raw provider state, owns subsequent
coverage counts and review resolution.
