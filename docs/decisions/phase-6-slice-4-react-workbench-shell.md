# Phase 6 Slice 4: React Workbench Shell, Hierarchy, Routing, And Entity Tabs

## ExecPlan Slice

Slice 4, `React Workbench Shell, Hierarchy, Routing, And Entity Tabs`.

## Goal

Build the browser navigation experience over the Slice 3 read-only web boundary: approved project selection, a responsive workbench layout, lazy hierarchy navigation, label-first search, entity tabs, and progressive technical detail disclosure.

## Files Modified

- `web-app/package.json`
- `web-app/package-lock.json`
- `web-app/src/App.tsx`
- `web-app/src/App.test.tsx`
- `web-app/src/styles.css`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/workbench/ProjectListPage.tsx`
- `web-app/src/workbench/ProjectWorkspace.tsx`
- `web-app/src/workbench/HierarchyNode.tsx`
- `web-app/src/workbench/EntityDetails.tsx`

## Implementation

- Added React Router routes for project selection and project workspaces.
- Added TanStack Query for server-owned project, hierarchy, search, and entity-detail data with stale/loading/error states.
- Added a project list, source summary, bounded lazy class hierarchy, label-first search results, responsive sidebar/workspace layout, and multi-entity tabs.
- Added label-first entity details for classes, properties, individuals, and inspection-only shapes, with IRIs and raw technical metadata behind an explicit technical-details disclosure.
- Added keyboard-focus-visible styling and accessible labels for search, hierarchy expansion, tabs, and tab close actions.
- Preserved the existing VS Code workbench and kept semantic interpretation in the Kotlin-backed web API.

## Tests And Verification

- Updated the app shell tests for project loading, navigation, label-first entity opening, technical-detail disclosure, and loading errors.
- Added transport/query helpers for project list loading and cached read-only data access.
- `npm test` passed: 4 test files, 8 tests.
- `npm run build` passed: TypeScript check and Vite production build.
- `git diff --check` passed.

## Decisions And Limitations

- React Router and TanStack Query were added because they are the approved Phase 6 frontend stack and were not included in the Slice 1 scaffold.
- Entity tabs are client-session state; shared staging, collaboration, editing, reasoning, SHACL, FIBO, and AI are later slices.
- Technical IRIs remain available for identity and follow-up requests but are not the default visual label.
- Shapes are inspection-only in this slice.
- `npm install` reported two high-severity audit findings in the installed dependency tree. No broad audit upgrade was performed because dependency upgrades are outside this slice.

## Git

Commit and remote branch are created after verification as part of the Slice 4 implementation workflow.
