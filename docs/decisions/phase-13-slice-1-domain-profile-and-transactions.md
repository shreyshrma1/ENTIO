# Phase 13 Slice 1: Domain Profile And Transaction Primitives

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-1-domain-profile`

## Scope

Slice 1 adds the typed project-domain contracts, optional profile loading, and
fixed-file transaction primitives required by the approved Phase 13 plan. It
does not expose activation through the server or browser, generate Phase 13
FIBO assets, add search dependencies, or materialize reused entities.

## Delivered contracts

- `DomainOntologyProfile` fixes the approved FIBO source, release, package
  fingerprint, managed source ID, and sidecar schema.
- `DomainOntologyProjectPaths` derives the profile, managed Turtle source,
  provenance log, journal, and transaction directory from fixed
  project-relative paths.
- availability, migration, structured issue, activation-preview,
  deactivation-preview, and deactivation-blocker contracts are typed in
  `core-types`.
- `EntioProject` exposes an optional active domain ontology without changing
  the hand-authored `EntioProjectConfig`.

## Profile loading

`DomainProfileRepository` treats an absent profile as inactive and accepts
only the exact approved five-field profile. Serialization is deterministic.
Malformed YAML, extra or missing fields, unsupported sources or schemas,
stale release or package fingerprints, missing managed sources, and fixed
paths that cross symbolic links produce structured failures.

`ProjectLoader` recovers an interrupted domain transaction before profile
loading. An active profile contributes the fixed `fibo-reuse` Turtle source to
the loaded aggregate without rewriting `entio.yaml`. Duplicate configured IDs
or canonical paths and an ontology declaration in the managed statement
container fail loading. An absent profile preserves the existing resolved
sources and semantic graph.

## Transaction behavior

`DomainFileTransactionManager` is restricted to the profile, managed source,
and provenance targets approved by the persistence ADR. It writes forced
temporary bytes and verified backups, records original and intended SHA-256
states in the fixed journal, and uses operation-specific replacement order.
Activation places the managed source before the profile commit point;
deactivation removes the profile first.

Commit verifies target hashes and domain semantics before closing the journal.
Recovery distinguishes all-original, all-intended, mixed, and unknown states.
It cleans an all-original preparation, semantically verifies an all-intended
state, restores a mixed state from verified backups, and preserves the journal
when bytes match neither state. An empty managed source orphaned before the
activation commit point is removed only when the journal proves its identity.

`DomainProfileService` provides non-mutating activation and deactivation
previews and transaction preparation methods only. Activation preparation creates
transaction artifacts but does not alter an ontology or expose public apply
wiring. Deactivation reports deterministic blockers and prepares removal only
when the managed source is empty and every supplied compatibility check passes.

## Verification

The required Slice 1 commands passed on 2026-08-08:

```text
./gradlew :core-types:test
./gradlew :semantic-engine:test --tests '*ProjectConfig*'
./gradlew :semantic-engine:test --tests '*DomainProfile*'
./gradlew :semantic-engine:build
git diff --check
```

Focused tests cover absent, valid, malformed, unsupported, stale, missing, and
unsafe profiles; stable serialization and repository round trips; inactive
and active project loading; duplicate managed sources; transaction preparation,
commit, rollback, crash recovery, unknown-state blocking, and orphan cleanup;
non-mutating previews; deactivation eligibility; and byte preservation in a
copied example project.

## Deferred work

The approved full-corpus descriptors and reviewed foundation profile begin in
Slice 2. Public activation, foundation selection, retrieval, reuse
materialization, web routes, and React changes remain deferred to their exact
later slices.
