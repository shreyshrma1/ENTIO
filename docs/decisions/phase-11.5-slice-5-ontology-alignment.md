# Phase 11.5 Slice 5: Current-Ontology Alignment

## Status

Completed on 2026-07-27.

## Implemented Boundary

Slice 5 adds one bounded, provider-backed alignment stage after connected modeling and reconciliation. The stage receives an Entio-built snapshot, records the applied-ontology and current-work fingerprints, and returns exactly one advisory action for every connected-model item.

The snapshot can represent applied local entities, imports, private draft work, shared staging, the current proposal, same-task items, durable prior provenance, and approved pinned FIBO entries. It also identifies the writable source choices available to the task.

## Deterministic Resolution

The provider cannot return an arbitrary IRI as an accepted match. It may refer only to a server-issued context reference ID. Kotlin resolves that ID to the canonical scope, entity IRI, and source ID from the project-scoped snapshot, then independently checks:

- that the reference is current and belongs to the same project snapshot;
- that curated FIBO entries come from an approved pinned source;
- that the entity category and label are a plausible match for the modeled item;
- that a requested target source is one of the supplied writable sources;
- that create actions do not claim existing targets;
- that reuse, extend, and revise actions have verified targets; and
- that domain and range assignments include an explicit rationale.

This prevents an absent concept such as `Payment` from being redirected to an unrelated available entity such as `Account`. It also prevents entity records such as `Customer`, `Loan`, `Account`, or `Invoice` from being accepted as unsupported relationship matches.

## Provider Contract

The OpenAI adapter uses a separate strict JSON schema and no tools. The prompt treats the connected model, reconciliation records, and ontology context as untrusted data. It directs the model to recommend reuse, extend, revise, create, split, merge, conflict review, leave unchanged, or unsupported without inventing context, sources, IRIs, relationships, or executable edits.

The result remains advisory. This slice does not create a final plan, stage changes, write ontology sources, retrieve external content, or alter the pinned FIBO package.

## Verification

Focused coverage verifies:

- every approved match scope;
- applied-local and approved FIBO resolution;
- no-match creation behavior through the action contract;
- current-work duplicate references;
- rejection of stale, cross-project, unrelated, and unapproved FIBO references;
- exact ontology and current-work fingerprints;
- one alignment call for the task; and
- strict OpenAI request and response handling without tools or credential exposure.

The Slice 5 verification commands are:

```bash
./gradlew :semantic-engine:test
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```
