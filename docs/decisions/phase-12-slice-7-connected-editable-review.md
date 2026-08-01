# Phase 12 Slice 7: Connected Editable Review

Status: Complete on 2026-07-31.

## Decision

Phase 12 grounded analysis now installs its verified interpretation context with
the existing in-memory connected review workspace. Each connected
recommendation exposes its grounded dispositions, server-issued selected and
alternative selection IDs, canonical IRIs, deterministic match reasons,
structural context, prerequisite origins, reviewer-solvable fields, and
`Executable`, `NeedsInput`, `ReviewOnly`, or `Blocked` status.

The browser renders this information only as server-owned review context. It
does not accept arbitrary existing-entity IRIs, decide compatibility, generate
operations, or write ontology data. Existing editable typed-operation fields
continue to round-trip through Kotlin final-plan verification and return the
recommendation to pending review before it can be accepted.

## Review And Batching

Recommendations remain collapsed by default. Their summary retains only the
name and edit type, confidence, review status, and native expansion control.
Expanded cards show semantic intent, exact typed changes, evidence, generated
IRIs, retrieved alternatives, sources, structural domain/range/datatype/type
context, prerequisite provenance, confidence, and safe warnings.

The historical `maximumAcceptedEdits` response field was removed. The existing
document-draft path remains the sole staging path: it preserves connected
recommendation groups and packs accepted typed edits into dependency-safe
atomic batches of at most 20 without imposing a task-wide semantic ceiling.

## Counts And Safety

The review response reports distinct totals for evidence blocks, retained and
rejected NLP candidates, retained/unresolved/rejected grounded items,
recommendation outcomes, and expanded typed edits. Review context remains
temporary and project/user scoped. Stale work keys and graph fingerprints,
individual confirmation gates, deterministic verification, proposal approval,
atomic apply, reload, rollback, and applied provenance remain unchanged.

The orchestrator handoff change was explicitly authorized for this slice so
verified grounded metadata could reach its existing review owner without a
second review workflow.

## Reviewer-Solvable State Correction

Live two-PDF review on 2026-07-31 exposed an implementation gap: `Unresolved`
grounded items were mapped directly to blocked final recommendations, and the
review adapter looked for editable-field IDs using a different prefix than the
verifier produced. The result was visible but impossible-to-resolve cards.

The corrected boundary retains unresolved evidence-backed items as
`NeedsInput`, associates their disposition, kind, label, and compatible
server-issued selection fields with the correct grounded item, and exposes an
explicit resolution form. A reviewer may choose reuse, writable extension, or
new creation, confirm the supported kind and label, and select only an
authorized retrieval selection. Kotlin then reruns grounded verification,
semantic compilation, final-plan verification, collision checks, and no-op
checks before the recommendation can become executable or review-only.

Reviewer-created object properties additionally require server-issued domain
and range class selections. Datatype properties require a domain selection and
an approved RDF datatype, and individuals require a type selection. Supplied
definitions compile as connected definition operations. These values are
converted into reviewer-provided grounded prerequisites and verified through
the existing compiler; the browser never constructs typed operations.

Review impact counts now distinguish executable, needs-input, review-only, and
genuinely unsafe recommendations. `Pending` is described as awaiting a reviewer
decision rather than as a compilation failure. Unsupported or incomplete
property, assertion, constraint, and individual choices remain non-acceptable
and return a safe actionable error instead of being silently deleted.

The correction was exercised end to end with both example PDFs and the
verified `gpt-5.6-luna` model. The repeated local stages produced 698 evidence
mentions and 112 ontology-bearing candidates. The final review contained 54
cards: 18 executable, 28 needing input, 8 reuse/review-only, and zero unsafe
blocked recommendations. Every input-required item exposed editable fields.
Resolving `Loan Rate` produced one draftable atomic recommendation containing
datatype-property creation, `Loan` domain, decimal range, and definition
operations; resolving `Loan Operations Manager` produced class creation and
definition operations. The connected-group anchor remains the edited
declaration, so reviewer-provided supporting classes cannot rename the card.
Neither exercise accepted, staged, applied, or changed ontology source data.

A subsequent Luna run exposed an additional empty-group case for grounded
`ExtendExisting` declarations that carried a distinct definition directly on
the declaration. Kotlin now compiles that attached meaning into `Add
Definition`; an unchanged definition remains review-only. The declaration,
not the synthetic definition operation, remains the connected card identity.
The validating two-PDF run produced 69 cards with 16 draftable, 44 needing
input, 9 reuse/review-only, and zero unsafe blocked recommendations. Its
`Commercial Account` extension was draftable with one `Add Definition`
operation, and every input-required item exposed reviewer fields.

## Actionable Review Outcome Correction

Follow-up review of the live two-PDF results showed that the user-facing
`ReviewOnly` bucket conflated two different outcomes and left cards with no
meaningful action. The review contract now presents exact existing-ontology
reuse as `Matched`. A matched card can be confirmed or rejected, never appears
blocked, and creates no ontology edit. The existing retain decision remains the
server-side confirmation mechanism, so this change does not introduce another
review or apply path.

Administrative, illustrative, and unsupported meanings remain in the complete
coverage record but no longer create recommendation cards. They are exposed in
a compact document-only coverage ledger with their reasons and evidence. When
unsupported context occurs inside an otherwise executable connected group, it
is moved to that ledger while the supported connected changes remain on the
actionable card. Modelable ambiguity and incomplete supported meaning continue
to use `NeedsInput`; `Blocked` is reserved for genuine safety failures.

The Kotlin planning and compiler compatibility states remain unchanged. The
adapter translates those internal states into the actionable public review
contract, and the React client no longer accepts `ReviewOnly` as a card or
grounded-item status. Focused server tests cover exact reuse confirmation and
administrative coverage retention. Browser tests cover matched confirmation,
the absence of document-only review cards, evidence access from the coverage
ledger, and safety-blocked behavior.

## Reviewer-Authorized Reuse Correction

The exact-identity provider gate is intentionally stricter than an explicit
review decision. When a reviewer resolves `NeedsInput` by choosing a compatible
server-issued reuse target, or chooses an existing class as a required domain,
range, or type, the review workspace marks only those generated reuse items as
reviewer-authorized. Kotlin still verifies the selection, kind, source,
fingerprints, connected roles, and complete compiled plan. This preserves the
reviewer's ability to make a justified broader mapping without allowing model
output to claim that mapping was an automatic exact match.

The integration regression fixture now treats the provider's non-exact
`Servicing Policy` to `Loan` mapping as actionable `NeedsInput`; the separate
reviewer-resolution test proves that selecting `Loan` as a datatype-property
domain remains valid and compiles through the existing review path.

## Qualified-Class Subclass Resolution

An unresolved class may now be created with an optional server-issued
superclass selection. For qualified meanings whose rejected provider reuse has
a verified lexical-head relationship, the form preselects Kotlin's suggested
superclass and explains that reviewer confirmation is required. The reviewer
may clear the suggestion, choose another authorized class, reuse the broader
entity directly, or reject the card.

On confirmation, the server constructs reviewer-provided connected support
items and reruns grounded verification, semantic compilation, collision and
no-op checks, and final-plan verification. The resulting card contains the
existing typed `CreateClass` and `AddSuperclass` operations in one draftable
connected recommendation. React sends selection IDs only and does not create
ontology operations or infer hierarchy.

Focused server coverage compiles a new class plus an existing superclass and
definition. Browser coverage verifies the suggested superclass is visible,
preselected, optional, and included in the explicit resolution request.

The bounded class-choice adapter always places the exact suggested selection ID
before canonical-IRI deduplication. If another candidate produced a higher-score
selection for the same canonical class, it cannot replace the item-specific
suggestion and leave the browser with a selected value that is absent from the
dropdown. The suggestion still counts once toward the existing bounded choice
limit.
