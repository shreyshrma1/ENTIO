# Phase 13 Slice 6: Managed Domain Reuse

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-6-managed-domain-reuse`

## Scope

Slice 6 turns a fresh, server-issued domain recommendation into reviewable
project changes. It adds managed FIBO reuse, reuse with supported
customization, local extension, close and related mappings, local continuation,
reuse removal, source-versus-project comparison, and applied provenance.

All ontology changes still use the shared staging, preview, validation,
approval, multi-source apply, reload, and rollback workflow. This slice adds no
second apply route, raw RDF input, UI integration, document-ingestion changes,
assistant changes, automatic equivalence, or write to the pinned FIBO package.

## Managed reuse behavior

The semantic engine resolves the canonical IRI against the verified full-corpus
descriptor asset and prepares only supported class, object-property, or
datatype-property statements. It preserves the canonical IRI and targets
materialized domain statements at the fixed `fibo-reuse` source. The prepared
batch records explicit and dependency entities, stable source snapshots,
omitted axioms, statement counts, and payload size.

Preparation is cycle safe and fails rather than truncating when the approved
closure, statement, byte, depth, or time bounds are exceeded. Safely omitted
anonymous or advisory source axioms produce an explicit partial-materialization
classification and require acknowledgement. Identity-bearing equivalence and
property-chain meaning is classified as unsupported and blocks reuse.

Customization distinguishes omitted request fields from explicit empty values,
so a user can preserve, replace, or remove supported labels, definitions,
parents, domains, and ranges. Custom references must resolve to the current
project, verified package, or approved RDF/OWL vocabulary. A new local extension
must use the configured project IRI namespace. Mapping is limited to
IRI-valued `skos:closeMatch` and `skos:relatedMatch` annotations on an existing
local subject and does not materialize or imply equivalence with the target.

Removal deletes only project-owned managed statements, leaves FIBO assets
unchanged, and blocks when a logical local dependency remains. Close and
related mappings do not block removal because they remain valid references to
the verified external record.

## Provenance and atomicity

Successfully applied domain work appends a checksummed
`entio-domain-reuse-provenance-v1` JSONL event under
`.entio/domain-reuse/events-v1.jsonl`. Each event retains package and record
identity, the bounded source snapshot, omitted axioms, dependency fingerprint,
proposal and change-set identity, actor and timestamp, baseline and result
fingerprints, customization classification, and prior record identity.

The repository uses prepared original and intended bytes plus a checksummed
journal. The existing atomic ontology applier prepares provenance before source
writes, commits it only after ontology reload and deterministic verification,
and restores it when ontology application rolls back. Startup recovery compares
the current project fingerprint with the journaled baseline and result. Failed
provenance finalization retains its recovery handle until rollback succeeds, so
an error cannot silently leave applied ontology and provenance divergent.

## Web contracts

The previously reserved recommendation stage route now resolves a current,
project- and user-bound recommendation on the server, verifies its permitted
action, prepares Kotlin-owned graph changes, and adds them to shared staging:

```text
POST /api/v1/projects/{projectId}/domain-recommendations/{recommendationId}/stage
GET  /api/v1/projects/{projectId}/domain-reuse/{entityId}
```

Clients submit typed action and customization values, not canonical source
statements. Continue-locally creates no domain change. The detail route returns
the pinned source snapshot, current project statements, stable differences, and
annotation-only or logical customization classification.

## Verification

The required commands passed on 2026-08-08:

```text
./gradlew :core-types:test --tests '*Phase13*'
./gradlew :semantic-engine:test --tests '*DomainReuse*'
./gradlew :semantic-engine:test --tests '*External*Proposal*'
./gradlew :semantic-engine:test --tests '*Atomic*Applier*'
./gradlew :web-server:test --tests '*DomainReuse*'
./gradlew :web-server:test --tests '*Staging*'
./gradlew :semantic-engine:verifyFiboCatalog
git diff --check
```

The repository-wide `./gradlew check` regression gate also passed.

Coverage includes class and property reuse, partial and unsupported source
meaning, customization and explicit removal, canonical-subject preservation,
local namespace enforcement, mappings, mapping no-ops, dependency-safe removal,
cycle ordering, hard contract bounds, source/project differences, provenance
append, checksum corruption, prepare/commit/finalize/rollback recovery, shared
staging, source-package immutability, existing Phase 5 translation, and atomic
applier regressions.

## Deferred work

Slice 7 owns the React domain settings, foundation selection, source/project
details, and unified Explore integration. Later approved slices add the
remaining human-driven workflow integrations and VS Code presentation. This
slice does not expose domain reuse through document ingestion or the ontology
assistant.
