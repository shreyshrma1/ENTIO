# Phase 13 Slice 11: Existing-Project Migration

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-11-existing-project-migration`

## Outcome

Entio now detects existing FIBO and OMG Commons use in inactive projects before
domain-profile activation. Detection is owned by the Kotlin semantic engine and
compares every domain IRI used as a subject, predicate, or object with the
verified `master_2026Q2` descriptor package.

The stable statuses are:

- `NoExistingReuse` when no domain IRI is present or the profile is already
  active;
- `ExistingReuseRecognized` when every detected identity belongs to the current
  approved package;
- `ExistingReuseAmbiguous` when current-package and unknown identities occur
  together;
- `ExistingReuseUnsupported` when none of the detected identities belongs to
  the current package.

Matching the current package establishes only that the current release supports
an identity. Entio never infers the project's historical source release from
RDF identity alone.

## Migration Preview

Recognized inactive projects can request a read-only migration preview through
the web or CLI. It shows the exact profile and managed-source paths, but it:

- does not activate the project;
- does not move RDF statements;
- does not create provenance;
- does not rewrite staged or proposed work;
- requires a normal reviewed semantic proposal for any later statement move.

Legacy RDF does not contain the proposal, actor, and apply identifiers required
by the Phase 13 provenance contract. The detector can identify exact current-
package declarations as possible evidence, but reports provenance seeding as
ineligible rather than inventing missing history.

## Existing Work

The semantic contract represents staged, proposed, rejected, and rolled-back
legacy work with its baseline-retention status. The web adapter inventories
current shared staged/proposal state. Existing baselines remain unchanged and
continue through the established stale-baseline checks after explicit profile
activation. Applied meaning remains in the project RDF; inactive historical
records are not deleted.

## Copied-Fixture Migration Report

The copied `examples/simple-ontology` fixture contains direct FIBO class use,
property use, ontology references, and local extensions. It is deterministically
classified as `ExistingReuseRecognized` against `master_2026Q2`. Adding one
unknown legacy-namespace identity changes the result to
`ExistingReuseAmbiguous` and blocks migration preview. A fixture containing
only the unknown identity is `ExistingReuseUnsupported`.

Previewing and activating the recognized copied fixture leaves its RDF graph
unchanged. A forced post-write configuration verification failure restores the
absence of both profile and managed-source files.

## Interfaces

- `GET /api/v1/projects/{projectId}/domain-migration` returns deterministic
  migration evidence and warnings.
- `POST /api/v1/projects/{projectId}/domain-migration/preview` returns the exact
  activation files only for recognized use.
- `domain-migration` and `domain-migration-preview` provide the same
  machine-readable, read-only diagnostics in the CLI.
- The React Domain settings page presents recognized, ambiguous, and
  unsupported states before allowing the existing explicit activation flow.

No automatic activation, release upgrade, statement movement, provenance
fabrication, proposal deletion, or unrelated project-configuration migration
was added.

## Verification

The Slice 11 verification commands are:

```text
./gradlew :semantic-engine:test --tests '*DomainMigration*'
./gradlew :web-server:test --tests '*DomainMigration*'
./gradlew :cli:test --tests '*DomainMigration*'
(cd web-app && npm test)
git diff --check
```
