# Phase 4 Slice 3: Project Graph Roles And Local Import Closure

## ExecPlan Slice Implemented

Slice 3: Project Graph Roles And Local Import Closure from `docs/execplans/0007-phase-4-owl-reasoning-shacl.md`.

## Goal

Add explicit ontology, data, and shapes roles to project sources and resolve local `owl:imports` closures without network access.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/EntioProject.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ProjectConfigLoader.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/OntologySourceResolver.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ImportClosureResolver.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProjectConfigLoaderTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/OntologySourceResolverTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ImportClosureResolverTest.kt`
- `docs/decisions/phase-4-slice-3-project-graph-roles-import-closure.md`

## Behavior Added

- Sources accept a `roles` list containing `ontology`, `data`, and/or `shapes`.
- Legacy sources without roles default to `ontology` and `data`; no source defaults to `shapes`.
- `importMappings` maps imported ontology IRIs to configured local source IDs.
- Local imports are traversed once per source, nested imports are included, and import cycles are reported without repeated loading.
- Missing and unknown mapped imports are reported as incomplete; cycles remain complete when all sources resolve.
- Import resolution parses only configured local files and never performs network lookup.

## Tests Added Or Updated

- Explicit graph-role and import-mapping configuration parsing.
- Legacy source-role defaults and role propagation through source resolution.
- Nested local import closure.
- Missing and unknown mapped import findings.
- Cycle detection with complete local closure.

## Verification

| Command | Result |
| --- | --- |
| `./gradlew :semantic-engine:test` | Initially exposed stale generated test-class discovery; resolved after clean build |
| `./gradlew clean :semantic-engine:test` | Passed |
| `./gradlew test` | Passed |

## Result

Project sources now carry explicit SHACL graph roles and local import configuration, and `ImportClosureResolver` returns deterministic closure membership and findings without silently downloading imports. OWL reasoning and SHACL validation remain unimplemented in this slice.

## Assumptions And Limitations

- `roles` is the selected Phase 4 YAML syntax for this implementation slice.
- `importMappings` is the explicit local mapping mechanism; direct file-URI imports are also recognized when they match a configured source path.
- Import findings are contracts and resolution behavior only. Reasoner execution and incomplete-import policy enforcement remain later-slice work.

## Git

- Commit: created for this slice; see Git history for the commit hash.
- Remote push: the slice branch is pushed after verification.
