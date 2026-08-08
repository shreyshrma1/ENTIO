# Phase 13 Project Domain Profile Persistence

Date: 2026-08-08

Status: Approved by the Phase 13 Slice 0 audit

## Context

Phase 13 makes domain-ontology use optional and project scoped. Activation must
not rewrite a user's hand-authored `entio.yaml`, and a loaded project must not
silently acquire FIBO merely because the package is installed. The profile,
managed Turtle source, provenance history, and interrupted-write recovery also
need one deterministic ownership and path contract.

## Decision

The active selection is the Entio-owned sidecar
`.entio/domain-profile.yaml`. Its complete Phase 13 schema is:

```yaml
schema: entio-domain-profile-v1
sourceId: fibo
release: master_2026Q2
packageFingerprint: 015142b94819291379b89c3bba92048f037f1d8e635d3f1342d29f0f02f374ad
managedSourceId: fibo-reuse
```

No additional or missing key is accepted in version 1. Values must exactly
match the approved package and fixed managed source. Absence means inactive.
A malformed, unsupported, stale, unsafe, or inconsistent sidecar blocks domain
features and reports a structured diagnostic; it never falls back to a
different release.

The loader derives, rather than serializes, these project-relative paths:

- managed source: `ontology/fibo-reuse.ttl`;
- provenance log: `.entio/domain-reuse/events-v1.jsonl`;
- transaction journal: `.entio/domain-transaction-v1.json`;
- transaction temporary and backup files: `.entio/domain-transaction-v1/`.

Every resolved path must remain beneath the real project root after
normalization and symbolic-link checks. Existing unexpected files are never
overwritten. There is at most one domain transaction per project.

## Transaction protocol

The semantic engine owns a reusable domain transaction primitive. A journal
uses schema `entio-domain-transaction-v1` and records a transaction ID,
operation, phase, and for every target its relative path, original state
(`absent` or SHA-256), intended state (`absent` or SHA-256), temporary path,
and verified backup path. Journal and prepared bytes are forced to storage
before replacement begins.

The phases are `Prepared`, `Replacing`, `Verifying`, and `Committed`.
Activation prepares the empty managed source first and replaces the profile
last; the profile is its commit point. Deactivation removes the profile first
only after all preconditions pass and removes an empty generated source during
verified cleanup. Ontology/provenance apply has observable all-or-nothing
recovery rather than claiming that multiple filesystem renames are intrinsically
atomic.

Startup recovery compares actual hashes with both journal states:

- all intended states: run semantic verification and finish the transaction;
- all original states: remove verified temporary data and close the journal;
- a mixture: restore every target from its verified backup, recheck all
  original hashes, then clean up;
- any unknown hash, missing required backup, unsafe path, or failed restore:
  preserve the journal and block project mutation with a recovery diagnostic.

An orphaned managed source may be deleted automatically only when the journal
proves that activation created it, the profile never committed, and the source
is byte-for-byte the approved empty Turtle representation. No non-empty or
unrecognized file is deleted automatically.

## Loaded-project behavior

`ProjectLoader` reads the optional profile after `entio.yaml` and before graph
loading. A valid active profile contributes one resolved managed source to the
loaded aggregate without changing `EntioProjectConfig.ontologySources` or the
file on disk. Duplicate source IDs, duplicate paths, a missing managed source,
or an unexpected ontology declaration fail loading with a domain-profile
diagnostic. Inactive projects retain their Phase 12 graph and fingerprints.

## Consequences

- Activation is explicit configuration, not an ontology proposal.
- The browser and server cannot supply arbitrary paths, releases, or package
  fingerprints.
- Existing projects remain inactive until the user confirms activation.
- The same transaction primitive can protect later managed-source/provenance
  work without introducing a database or a second ontology apply route.
- Crash recovery is deterministic and testable with fault injection.
