# Phase 11.5 Slice 6: Modeling Critic And Confidence Checks

## Status

Completed on 2026-07-27.

## Implemented Boundary

Slice 6 adds one separate, bounded modeling-critic call after ontology alignment. It uses the same verified task-pinned OpenAI model and credential as earlier stages, but has its own prompt, request schema, response schema, stage record, and input/output hashes.

The critic receives verified discoveries, the connected model, reconciliation records, alignment records, and the bounded ontology snapshot. It can report approve, revise, split, replace, downgrade, reject, and request-clarification findings against supplied model-item or alignment IDs.

## Advisory-Only Behavior

Critic findings never mutate discoveries, connected-model items, reconciliation records, alignments, sources, drafts, or proposals. The critic has no repair loop and no authority to stage, approve, apply, or write ontology changes. It returns concise reasons rather than hidden reasoning.

The prompt explicitly checks for:

- administrative metadata promoted into business meaning, including `Compliance Status` derived only from a document status field;
- unsupported or unjustified domain and range;
- missing supporting concepts and relationships;
- conditional rules flattened into simple fields;
- illustrative individuals treated as production individuals;
- unsupported `Customer → Loan` or `Account → Invoice` connections; and
- weak alignment and confidence choices.

## Confidence Ownership

Kotlin calculates an independent baseline for each model item and alignment:

- evidence confidence is the weakest linked verified discovery score;
- modeling confidence begins at 100 before critic downgrades; and
- ontology-fit confidence comes from the verified alignment.

The provider supplies the three dimensions for each finding. Kotlin rejects any attempted increase, applies only decreases, and calculates overall confidence as the minimum of evidence, modeling, and ontology fit. A `Downgrade` finding must lower at least one dimension, while another finding action cannot silently change confidence.

The existing final-plan contracts require every critic finding to receive exactly one final disposition: accepted and incorporated, rejected with a rationale, or unresolved. Unresolved dispositions remain blocking.

## Verification

Focused tests cover:

- metadata-derived business concepts;
- weak domain/range and missing support;
- flattened complex rules;
- illustrative individuals;
- confidence downgrades and weakest-dimension calculation;
- duplicate, unknown, and bounded target handling;
- reuse of the task model with distinct critic prompt/schema versions; and
- strict OpenAI requests with no tools or credential exposure.

The Slice 6 verification commands are:

```bash
./gradlew :core-types:test
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```
