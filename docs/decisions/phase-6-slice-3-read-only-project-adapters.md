# Phase 6 Slice 3: Read-Only Project Adapters

## ExecPlan Slice

Slice 3, `Read-Only Project Adapters`.

## Goal

Expose project summaries, ontology sources, lazy label-first class hierarchy pages, entity details, and semantic search through typed web-server responses backed by the existing Kotlin semantic services.

## Files Modified

- `web-server/build.gradle.kts`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/ReadOnlyProjectAdapters.kt`
- `web-server/src/test/kotlin/com/entio/web/ApplicationTest.kt`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/projectApi.test.ts`

## Implementation

- Added read-only project summary and ontology-source endpoints.
- Added bounded hierarchy pagination with stable label-first ordering and child counts.
- Added entity detail responses with labels, definitions, annotations, source metadata, typed references, incoming relationships, and outgoing relationships.
- Added label-aware semantic search responses with kind, source, rank, and match reason metadata.
- Added typed browser helpers for the read-only web endpoints.
- Kept filesystem roots server-owned through the existing project registry and delegated loading, parsing, description assembly, and search to existing semantic-engine services.

## Tests And Verification

- Added Ktor route coverage for summary, hierarchy, search, entity relationships, missing entities, and invalid queries.
- Added frontend transport-helper coverage for IRI encoding, paging, entity details, and search requests.
- `./gradlew :web-server:test --no-daemon --console=plain -q` passed.
- `./gradlew test --no-daemon --console=plain -q` passed.
- `(cd web-app && npm test && npm run build)` passed: 4 test files, 7 tests.
- `git diff --check` passed.

## Decisions And Limitations

- The adapter reloads the registered project for each read request; caching and invalidation are outside this slice.
- The hierarchy is class-based and bounded by `offset` and `limit`; full-tree loading is not exposed.
- IRIs remain in transport responses for stable identity and follow-up requests, while labels are supplied as the default display value for clients.
- This slice does not add editing, proposal mutation, collaboration, reasoning jobs, SHACL jobs, FIBO retrieval, AI, or CLI subprocess execution.

## Git

Commit and remote branch are created after verification as part of the Slice 3 implementation workflow.
