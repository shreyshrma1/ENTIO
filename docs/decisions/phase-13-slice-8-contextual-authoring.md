# Phase 13 Slice 8: Contextual Authoring Recommendations

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-8-contextual-authoring`

## Scope

Slice 8 integrates one shared, server-ranked domain recommendation panel into
the existing class, property, individual, annotation, hierarchy, domain,
range, datatype, assertion, and value authoring surfaces. Recommendations are
available only while the optional domain profile is active. They supplement
the existing local forms and never replace, submit, or stage those forms.

The executable completeness matrix covers 12 authoring workflows and records
the operation kind, strict requested entity kind, context fields, owning UI
component, slice, and acceptance condition. Property hierarchy suggestions are
advisory because the existing typed-edit contract has no superproperty
operation; this slice does not invent a second or unsupported write path.

## Retrieval and server ownership

The panel waits 300 ms after input changes, issues a bounded request through
React Query, passes its cancellation signal to the HTTP request, and keys each
request by the complete modeling intent. Superseded responses cannot replace
the current result. Inactive profiles produce no recommendation search.
Degraded and unavailable retrieval remains visible without blocking local
authoring, and low-confidence candidates start collapsed.

The web server requires an explicit entity kind for contextual operations,
enforces fixed operation-to-kind contracts, resolves supplied project IRIs,
checks class/property context kinds, permits explicit XSD datatype IRIs, and
rejects unknown sources. Ranking, compatibility, recommendation identity,
dependencies, source/project comparison, and staging remain Kotlin-owned. The
browser contains no ranking or domain/range compatibility implementation.

## Review actions

Selecting a recommendation loads the server-issued source snapshot and the
current project difference. The user may explicitly reuse, customize, extend,
map close, map related, continue locally, or dismiss when the server permits
that action. Changing the action obtains a newly keyed server dependency
preview. Customization changes the project label or definition while retaining
the canonical external IRI. Partial materialization still requires explicit
acknowledgement.

All accepted domain actions enter the existing shared review queue. The host
authoring form remains unchanged before and after a domain action, and ordinary
local authoring remains available throughout.

## Verification

The required commands passed on 2026-08-08:

```text
(cd web-app && npm test)
(cd web-app && npm run build)
./gradlew :web-server:test --tests '*DomainRecommendation*'
./gradlew :semantic-engine:test --tests '*DomainRecommendation*'
git diff --check
```

Results were 25 Vitest files and 113 tests passed, the production
TypeScript/Vite build passed, and the focused server and semantic-engine
recommendation tests passed. Focused coverage includes the completeness
matrix, strict cross-kind rejection, project-context resolution, XSD datatype
context, debounce, cancellation, current-context replacement, low-confidence
collapse, inactive/degraded/unavailable states, regenerated action previews,
explicit staging, form preservation, and source-customization advice.

One initial semantic-engine verification run encountered duplicate compiled
`* 2.class` files in ignored Gradle output. Cleaning only the generated
semantic-engine output removed those invalid JVM test-class names; a clean
compilation and the exact required test filter then passed.
