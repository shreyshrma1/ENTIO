# Entio Phase 6 UI Redesign Specification

## Status

Approved design direction for implementation planning.

## Purpose

This document defines the visual design, information architecture, interaction model, component system, responsive behavior, and frontend implementation requirements for the Entio web workbench.

The goal is to replace the current clunky browser presentation with a professional ontology workbench that is:

- Easier to understand than Protégé.
- Faster to navigate than Protégé.
- Visually modern without becoming a generic dashboard.
- Dense enough for ontology professionals.
- Accessible to users who do not know RDF or OWL terminology.
- Faithful to Entio's existing staged-change, review, reasoning, SHACL, FIBO, collaboration, and AI workflows.
- Implemented entirely as an additive frontend redesign over existing HTTP and WebSocket contracts.

This is a frontend design specification. It must not change ontology semantics, proposal behavior, reasoning behavior, SHACL behavior, FIBO ranking, collaboration authority, or source-writing behavior.

## Source Of Truth And Non-Regression Rule

The existing Kotlin services remain authoritative for:

- Project loading.
- RDF and Turtle handling.
- Entity descriptions.
- Hierarchy and search.
- Typed edit translation.
- Validation.
- Staged changes.
- Combined proposal previews.
- Semantic diffs.
- Reasoning impact.
- SHACL impact.
- FIBO browsing and search.
- Approval, rejection, application, reload, and rollback.
- User permissions and collaboration conflicts.
- AI suggestion validation.

The web application may:

- Arrange information.
- Hide or reveal technical details.
- Preserve browser-local tab and form state.
- Render existing server states.
- Submit typed requests through existing contracts.

The web application must not:

- Infer ontology facts.
- Re-rank search results.
- Change validation policy.
- Interpret SHACL independently.
- Construct raw RDF changes.
- Write Turtle.
- Apply changes directly.
- Convert a backend failure into visual success.
- Remove or alter existing backend functionality.
- Break the VS Code extension or CLI.

If the UI requires semantic data that is not exposed by an existing contract, Codex must stop and identify the missing additive contract instead of inventing the answer in TypeScript.

---

# 1. Product Design Thesis

## 1.1 Better Than Protégé Means Progressive Depth

Entio should not beat Protégé by hiding technical depth.

It should beat Protégé by presenting the same depth in layers:

1. **Human meaning first**
   - Label.
   - Definition.
   - Entity type.
   - Relationships in readable language.

2. **Ontology structure second**
   - Parents.
   - Children.
   - Domain.
   - Range.
   - Assertions.
   - Constraints.
   - Inferences.

3. **Technical representation on demand**
   - Full IRI.
   - Source file.
   - Raw RDF statements.
   - Fingerprints.
   - Constraint details.
   - Match-score breakdowns.

A new user should be able to understand an entity without seeing a raw triple.

An expert should be able to reach the raw technical details in one action.

## 1.2 The Interface Must Feel Like A Workbench

Do not design Entio as:

- A wall of dashboard cards.
- A marketing site.
- A chat application with ontology features attached.
- A form wizard.
- A raw graph visualization.
- A collection of unrelated pages.

Design Entio as a persistent professional workbench with:

- Stable navigation.
- Resizable panes.
- Tabs.
- Search.
- Contextual actions.
- Review surfaces.
- Background job status.
- Progressive disclosure.

## 1.3 The Main Product Loop Must Always Be Visible

The UI should continuously reinforce:

```text
Explore
→ Draft
→ Stage
→ Preview
→ Review impact
→ Approve or reject
→ Apply
```

Users should never wonder:

- Whether a form changed the source.
- Whether a change is only drafted.
- Whether a change is staged.
- Whether reasoning is current.
- Whether SHACL results belong to the applied graph or a preview.
- Whether another user has changed the baseline.

## 1.4 Collaboration Must Be Present But Quiet

Do not fill the interface with cursors, floating labels, or constant motion.

Show collaboration through:

- Avatar groups.
- Small activity indicators.
- Author metadata on staged changes.
- A compact presence tooltip.
- Conflict banners.
- Activity timeline.
- "Viewing" or "Editing" indicators beside relevant entities.

Ontology content remains the primary focus.

---

# 2. Design Principles

## 2.1 Label First, IRI Available

Primary lists and headings use labels.

IRIs appear in:

- Copyable secondary lines.
- Technical-details drawers.
- Tooltips where useful.
- Search result details.
- Raw RDF views.

Do not display full IRIs as primary navigation text.

## 2.2 Compact, Not Cramped

The workbench should support high information density while maintaining:

- Clear grouping.
- Consistent alignment.
- 8-pixel spacing rhythm.
- Readable line lengths.
- Distinguishable interactive controls.
- Predictable row heights.

## 2.3 Stable Spatial Model

Major tools stay in stable locations:

- Global modules on the far-left rail.
- Ontology navigation in the left sidebar.
- Open work in the center.
- Contextual editing and AI in the right drawer.
- Staged changes and impact in the bottom dock or dedicated Changes workspace.
- Global status in the header.

Do not move the same function between unrelated locations.

## 2.4 No Modal-Driven Core Workflow

Use modals only for:

- Destructive confirmation.
- Credential entry.
- Short project/session choices.

Use:

- Right-side drawers for editing.
- Tabs for entity work.
- Inline expansion for details.
- Dedicated review workspaces for proposals.

Avoid nested modals.

## 2.5 Meaningful Color, Never Color Alone

Every semantic status must use:

- Color.
- Icon.
- Text label or accessible tooltip.

Examples:

- Asserted.
- Inferred.
- External.
- Staged.
- Invalid.
- Warning.
- Conflict.
- Stale.
- AI-generated.

## 2.6 Every Background Result Has A Fingerprint Context

Reasoning and SHACL status must visibly identify whether the result is:

- Current for the applied graph.
- Current for the proposal preview.
- Running.
- Stale.
- Failed.
- Incomplete.

Do not show old semantic results as current.

---

# 3. Approved Frontend Structure

## 3.1 Application Layout

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ Global Header                                                              │
├──────┬──────────────────────────┬───────────────────────────────┬────────────┤
│ App  │ Context Sidebar          │ Main Tab Workspace            │ Inspector  │
│ Rail │ Ontology Tree / Filters  │ Entity / Review / FIBO / etc. │ or AI      │
│      │                          │                               │ Drawer      │
├──────┴──────────────────────────┴───────────────────────────────┴────────────┤
│ Optional Bottom Dock: staged changes / jobs / activity                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3.2 Default Dimensions

Optimized reference viewport:

- 1440 × 900.

Minimum supported desktop viewport:

- 1024 × 720.

Default dimensions:

- Global header: `52px`.
- App rail: `56px`.
- Context sidebar: `312px`.
- Context sidebar minimum: `240px`.
- Context sidebar maximum: `480px`.
- Inspector drawer: `368px`.
- Inspector minimum: `320px`.
- Inspector maximum: `520px`.
- Bottom dock default height: `280px`.
- Bottom dock minimum: `180px`.
- Bottom dock maximum: `480px`.

All major panes must be resizable.

Persist pane sizes in browser-local workspace state.

## 3.3 Responsive Behavior

### Width 1440px and above

- App rail visible.
- Left sidebar visible.
- Main workspace visible.
- Right inspector can remain pinned.
- Bottom dock optional.

### Width 1180px to 1439px

- App rail visible.
- Left sidebar visible.
- Right inspector opens as overlay drawer by default.
- Bottom dock remains available.

### Width 1024px to 1179px

- App rail visible.
- Left sidebar collapsible.
- Inspector always overlays.
- Tab overflow uses horizontal scrolling and a tab list menu.

### Below 1024px

Phase 6 is not mobile-first.

- Show a supported-but-constrained layout.
- Collapse sidebar and inspector.
- Preserve access to all actions.
- Do not attempt a mobile redesign.

---

# 4. Information Architecture

## 4.1 App Rail

The app rail is the leftmost global module switcher.

Required destinations:

1. **Explore**
2. **Changes**
3. **Reasoning**
4. **Constraints**
5. **FIBO**
6. **Activity**
7. **AI Assistant**
8. **Settings**

Use Lucide icons with text tooltips.

The selected destination uses:

- Primary-subtle background.
- Primary icon.
- 3px left active indicator.
- Accessible `aria-current`.

Do not use different arbitrary colors for each module.

## 4.2 Global Header

Left side:

- Entio mark.
- Project selector.
- Current project label.
- Applied graph status.

Center:

- Global command/search field.
- Placeholder: `Search ontology, FIBO, commands…`
- Shortcut hint: `⌘K` or `Ctrl+K`.

Right side:

- Reasoning status chip.
- SHACL status chip.
- Shared staged-change count.
- Connected-user avatar group.
- AI credential status.
- Current user menu.

The header must remain visually calm. Status chips should be compact.

## 4.3 Context Sidebar By Module

### Explore

- Project sources.
- Search/filter field.
- View selector:
  - Hierarchy.
  - Classes.
  - Properties.
  - Individuals.
  - Shapes.
- Ontology tree.

### Changes

- Staged changes.
- Filters:
  - All.
  - Mine.
  - AI.
  - Conflicts.
  - Invalid.
- Author avatars.
- Status counts.

### Reasoning

- Current applied result.
- Preview result.
- Inferred relationships.
- Unsatisfiable classes.
- Explanations.

### Constraints

- SHACL findings.
- Severity filters.
- Shapes list.
- Validation mode indicator.

### FIBO

- Curated modules.
- Search.
- Entity-kind filter.
- Module filter.
- Maturity filter.

### Activity

- Project timeline.
- User filter.
- Event type filter.

---

# 5. Visual Design System

## 5.1 Styling Strategy

Use:

- CSS custom properties for tokens.
- CSS Modules or the project's existing scoped styling system.
- Radix UI primitives for accessible popovers, dialogs, dropdown menus, tooltips, tabs, and scroll areas.
- Lucide React icons.
- `react-resizable-panels` for pane resizing.
- `@tanstack/react-virtual` for large trees and lists.
- TanStack Query for server state.
- Zustand or local React state only for browser-local workspace state.
- CSS transitions rather than a general animation framework.

Do not introduce Material UI, Ant Design, Bootstrap, or another large opinionated UI framework.

These frameworks would make Entio look generic and make density harder to control.

## 5.2 Font Stack

```css
--font-sans:
  Inter,
  ui-sans-serif,
  system-ui,
  -apple-system,
  BlinkMacSystemFont,
  "Segoe UI",
  sans-serif;

--font-mono:
  "JetBrains Mono",
  "SFMono-Regular",
  Consolas,
  "Liberation Mono",
  monospace;
```

Use the system fallback if the named font is unavailable.

Do not bundle font files into the repository unless already approved.

## 5.3 Type Scale

| Token | Size | Weight | Use |
|---|---:|---:|---|
| `text-xs` | 11px | 500 | metadata, badges |
| `text-sm` | 12px | 400/500 | tree rows, secondary text |
| `text-body` | 14px | 400 | primary body |
| `text-body-strong` | 14px | 600 | labels and row titles |
| `text-section` | 16px | 600 | section headings |
| `text-title` | 20px | 650 | entity title |
| `text-page` | 24px | 650 | full workspace page title |

Default line height:

- `1.45` for body.
- `1.25` for headings.

## 5.4 Spacing Scale

Use an 8px base rhythm with 4px half-steps:

```text
2, 4, 8, 12, 16, 20, 24, 32, 40, 48
```

Do not use arbitrary one-off spacing values unless required for alignment.

## 5.5 Shape Tokens

```css
--radius-sm: 6px;
--radius-md: 8px;
--radius-lg: 12px;
--radius-xl: 16px;
```

Use:

- `6px` controls and badges.
- `8px` cards and menu surfaces.
- `12px` drawers and major panels.
- `16px` only for empty-state illustrations or large overlays.

Avoid overly rounded pill-shaped layouts.

## 5.6 Light Theme Tokens

```css
:root {
  --bg-app: #f5f7fa;
  --bg-sidebar: #f8f9fb;
  --surface-1: #ffffff;
  --surface-2: #fafbfc;
  --surface-3: #f1f3f6;

  --border-subtle: #e5e7eb;
  --border-default: #d8dde6;
  --border-strong: #b8c0cc;

  --text-primary: #172033;
  --text-secondary: #475467;
  --text-muted: #667085;
  --text-disabled: #98a2b3;

  --primary-50: #eef2ff;
  --primary-100: #e0e7ff;
  --primary-500: #6366f1;
  --primary-600: #4f46e5;
  --primary-700: #4338ca;

  --asserted: #2563eb;
  --asserted-subtle: #eff6ff;

  --inferred: #7c3aed;
  --inferred-subtle: #f5f3ff;

  --external: #0f766e;
  --external-subtle: #f0fdfa;

  --staged: #b45309;
  --staged-subtle: #fffbeb;

  --success: #15803d;
  --success-subtle: #f0fdf4;

  --warning: #b45309;
  --warning-subtle: #fffbeb;

  --danger: #b42318;
  --danger-subtle: #fef3f2;

  --stale: #667085;
  --stale-subtle: #f2f4f7;

  --focus-ring: #6366f1;
}
```

## 5.7 Dark Theme

The token system must support dark mode later.

A complete dark theme is not required before the core light theme matches this specification.

Do not block the redesign on dark mode.

## 5.8 Shadows

Use restrained shadows:

```css
--shadow-sm: 0 1px 2px rgba(16, 24, 40, 0.05);
--shadow-md:
  0 4px 8px -2px rgba(16, 24, 40, 0.08),
  0 2px 4px -2px rgba(16, 24, 40, 0.04);
--shadow-overlay:
  0 16px 32px -8px rgba(16, 24, 40, 0.18);
```

Most layout separation should use borders, not shadows.

## 5.9 Motion

Use motion only to reinforce state.

Timing:

- Hover and selection: `120ms`.
- Drawer and panel transitions: `180ms`.
- Toast entrance: `160ms`.
- Tree expand/collapse: `140ms`.

Honor `prefers-reduced-motion`.

Avoid:

- Springy motion.
- Large page transitions.
- Animated gradients.
- Constant pulsing.

---

# 6. Semantic Visual Grammar

## 6.1 Asserted

Visual:

- Blue solid dot or link icon.
- Label: `Asserted`.
- Solid relationship connector where shown graphically.

## 6.2 Inferred

Visual:

- Purple sparkle or branch icon.
- Label: `Inferred`.
- Dashed connector where shown graphically.

Never display inferred facts without an explicit inferred marker.

## 6.3 External / FIBO

Visual:

- Teal external-link or library icon.
- Label: `FIBO` or `External`.
- Original IRI available in technical details.

## 6.4 Staged

Visual:

- Amber dot.
- Label: `Staged`.
- Tab or row receives a small amber marker.

## 6.5 AI-Generated

Visual:

- Violet sparkle icon.
- Label: `AI suggestion`.
- Author remains the user who accepted/staged it.
- Do not use a magical gradient border.

## 6.6 SHACL And Validation

- Violation: red shield icon and `Violation`.
- Warning: amber triangle and `Warning`.
- Information: blue info icon and `Info`.
- Valid: green check and `Valid`.

## 6.7 Stale And Conflict

- Stale: gray clock/refresh icon and `Stale`.
- Conflict: red merge-warning icon and `Conflict`.
- Include recovery action in the same surface.

Do not use color alone.

---

# 7. Core UI Primitives

Implement these reusable components before feature screens.

## 7.1 Required Primitives

- `Button`
- `IconButton`
- `SplitButton`
- `TextInput`
- `TextArea`
- `Select`
- `Combobox`
- `Checkbox`
- `RadioGroup`
- `Switch`
- `Tooltip`
- `Popover`
- `DropdownMenu`
- `Dialog`
- `Drawer`
- `Tabs`
- `Badge`
- `StatusBadge`
- `Avatar`
- `AvatarGroup`
- `Breadcrumbs`
- `SectionHeader`
- `EmptyState`
- `ErrorState`
- `Skeleton`
- `Toast`
- `InlineAlert`
- `DataTable`
- `VirtualList`
- `KeyValueRow`
- `CopyableText`
- `CodeBlock`
- `DiffRow`
- `TimelineItem`
- `ResizablePane`
- `CommandPalette`

## 7.2 Control Sizes

- Small control height: `28px`.
- Default control height: `36px`.
- Large control height: `42px`.
- Tree row height: `28px`.
- Standard list row: `36px`.
- Dense data row: `32px`.
- Tab height: `38px`.

## 7.3 Button Hierarchy

Primary:

- One per action group.
- Used for `Preview & stage`, `Approve & apply`, and high-confidence forward action.

Secondary:

- Neutral bordered button.

Ghost:

- Toolbar and inline actions.

Danger:

- Only for destructive actions.

Do not use several visually equal primary buttons in one view.

---

# 8. Global Navigation And Search

## 8.1 Global Search / Command Palette

Shortcut:

- macOS: `⌘K`
- Windows/Linux: `Ctrl+K`

Search across:

- Local classes.
- Properties.
- Individuals.
- SHACL shapes.
- Open tabs.
- Commands.
- FIBO when explicitly toggled or prefixed.

Result groups:

1. Current project.
2. Open tabs.
3. Commands.
4. FIBO.

Each result shows:

- Type icon.
- Label.
- Entity kind.
- Source.
- Short context.
- IRI only in secondary technical line.

Keyboard:

- Arrow keys navigate.
- Enter opens.
- `Cmd/Ctrl+Enter` opens in a new tab where applicable.
- Escape closes.

## 8.2 Breadcrumbs

Entity breadcrumb example:

```text
Finance Ontology / Classes / Loan / Commercial Loan
```

Use labels, not IRI segments.

Each breadcrumb is clickable.

---

# 9. Ontology Sidebar And Hierarchy

## 9.1 Sidebar Header

Required:

- Search field.
- Filter button.
- View-mode control.
- Collapse button.

## 9.2 Tree Row Anatomy

```text
[chevron] [type icon] Label                  [status] [activity avatar]
```

Elements:

- Chevron only when children exist or may exist.
- Type icon.
- Label.
- Optional status marker.
- Optional collaborator avatar.
- Context menu on hover/focus.

The row must support:

- Single click: select/open current tab.
- `Cmd/Ctrl+click`: open new tab.
- Enter: open.
- Arrow right: expand.
- Arrow left: collapse or move to parent.
- Context menu:
  - Open.
  - Open in new tab.
  - Edit.
  - Add subclass.
  - Copy IRI.
  - View source.
  - Delete, where supported.

## 9.3 Inferred Hierarchy

When the hierarchy includes inferred relationships:

- Asserted child edge uses normal row styling.
- Inferred-only placement uses a purple inferred marker.
- A tooltip states why it appears.
- Provide a filter:
  - Asserted only.
  - Asserted + inferred.

Default:

- Asserted + inferred if the latest reasoning result is current.
- Asserted only if reasoning is unavailable or stale.

## 9.4 Tree Performance

Use virtualization.

Do not load the complete tree on project open.

Requirements:

- Lazy child request.
- Child count.
- Loading skeleton rows.
- Retry state per node.
- Obsolete requests ignored after collapse.
- Stable ordering.
- Preserve expanded node IDs during the browser session.

---

# 10. Main Tab System

## 10.1 Tab Types

- Project overview.
- Class.
- Object property.
- Datatype property.
- Annotation property.
- Individual.
- SHACL shape.
- Reasoning.
- SHACL results.
- FIBO browser.
- Proposal review.
- Activity.
- AI conversation.

## 10.2 Tab Anatomy

```text
[type icon] Label [status dot] [close]
```

State markers:

- Amber dot: staged changes affect entity.
- Red dot: conflict or blocking validation.
- Purple dot: AI suggestion exists.
- Teal dot: external FIBO entity.
- Gray clock: stale data.

## 10.3 Tab Behavior

- Tabs can be reordered.
- Middle click closes.
- Unsaved browser-local form drafts trigger confirmation on close.
- Staged changes do not trigger draft confirmation because they are server state.
- Reopening an entity reuses its existing tab.
- Pinned tabs remain leftmost.
- Overflow uses horizontal scroll plus a searchable tab list.

## 10.4 Deep Linking

Entity and workspace routes should be shareable.

Opening a deep link should:

- Load the project.
- Open the corresponding tab.
- Select the matching sidebar item where practical.

---

# 11. Entity Detail Design

## 11.1 Entity Header

```text
[entity icon] Commercial Loan           [Class] [Local] [Staged]
              A loan used for...
              Finance Ontology › lending.ttl

                                            [Edit] [More ▾]
```

Secondary technical row:

```text
IRI: https://example.com/ontology#CommercialLoan   [copy]
```

The IRI row may be collapsed by default.

## 11.2 Entity Internal Navigation

Use horizontal section tabs:

- Overview.
- Relationships.
- Usage.
- Constraints.
- Activity.
- RDF.

Do not create a new top-level browser route for every section.

## 11.3 Overview

Use structured sections, not a card wall.

Recommended order:

1. Definition.
2. Alternate labels.
3. Direct parents.
4. Direct children.
5. Key properties.
6. Reasoning summary.
7. SHACL summary.
8. Source metadata.

Each section:

- Has a concise heading.
- Shows a count where relevant.
- Supports `View all`.
- Uses rows, not oversized cards.

## 11.4 Relationships

For classes:

- Asserted parents.
- Inferred parents.
- Direct subclasses.
- Properties by domain.
- Properties by range.
- Typed individuals.

For properties:

- Domain.
- Range.
- Super-properties if available.
- Assertions.
- Inverse/transitive inference where supported.

For individuals:

- Asserted types.
- Inferred types.
- Object-property assertions.
- Datatype values.

Each relationship row is clickable and opens the related entity.

## 11.5 Usage

Show where the entity is used:

- As parent.
- As domain.
- As range.
- As type.
- In assertions.
- In SHACL shapes.
- In staged changes.

## 11.6 Constraints

Show:

- Relevant shapes.
- Current findings.
- Severity.
- Message.
- Focus/path.
- Validation mode.

Inspection-only unless a supported typed SHACL edit backend exists.

## 11.7 RDF

Technical view.

Include:

- Source-filtered statements.
- Search.
- Copy.
- Monospace rendering.
- Asserted-only by default.
- Separate inferred view.

Do not make RDF the default tab.

---

# 12. Editing Experience

## 12.1 Editing Surface

Open editing in the right inspector drawer.

Do not navigate away from the entity.

Drawer header:

- Edit type.
- Target entity.
- Draft status.
- Close.

Drawer body:

- Focused typed form.
- Contextual help.
- Field-level validation.

Drawer footer:

- `Cancel`
- `Preview & stage`

## 12.2 Edit Entry Points

Primary `Edit` button opens an operation menu based on entity kind.

Class actions:

- Edit label.
- Edit definition.
- Add alternate label.
- Add superclass.
- Remove superclass.
- Create subclass.
- Delete.

Property actions:

- Edit label.
- Edit definition.
- Set domain.
- Set range.
- Add assertion where applicable.
- Delete.

Individual actions:

- Edit label.
- Assign type.
- Add object-property value.
- Add datatype value.
- Delete.

## 12.3 Preview & Stage Behavior

On click:

1. Disable primary button.
2. Show `Validating…`.
3. Call existing typed preview boundary.
4. On success:
   - Add to shared staged set.
   - Clear form.
   - Close drawer by default.
   - Show toast with `View staged change`.
5. On validation failure:
   - Keep all inputs.
   - Focus first invalid field.
   - Show field-level and summary errors.
6. On stale baseline:
   - Keep inputs.
   - Show stale banner.
   - Offer `Reload context`.

Do not claim the source changed.

## 12.4 Destructive Actions

Delete requires:

- Dependency review.
- Explicit dependent-statement selection.
- Clear human-readable summary.
- Typed confirmation only when impact is high.

Do not use generic `Are you sure?` copy.

---

# 13. Shared Staged Changes

## 13.1 Bottom Dock Summary

Collapsed bar:

```text
Changes  4 staged  •  1 conflict  •  2 contributors           [Review]
```

Expand to reveal:

- Staged list.
- Jobs.
- Activity tabs.

## 13.2 Staged Change Row

```text
[avatar] Create class “Commercial Loan”
         by Maya • 2 min ago • lending.ttl
         [Valid] [AI suggestion]
                                      [Edit] [Remove] [More]
```

Required metadata:

- Author.
- Time.
- Edit type.
- Target.
- Source.
- Validation.
- Conflict state.
- AI flag.

## 13.3 Filters

- All.
- Mine.
- AI.
- Invalid.
- Conflicted.
- By source.

## 13.4 Conflict Row

Use an inline red alert:

```text
This change was prepared against an older graph.
[Re-prepare] [Edit] [Discard]
```

Do not hide the change.

Do not allow it into application.

---

# 14. Proposal Review Workspace

## 14.1 Layout

```text
┌───────────────────────────┬──────────────────────────────────────────┐
│ Staged Changes            │ Proposal Impact                          │
│                           │                                          │
│ 1. Create class           │ [Explicit] [Reasoning] [SHACL] [Files]  │
│ 2. Add superclass         │                                          │
│ 3. Add definition         │ Human-readable impact rows               │
│                           │                                          │
│                           │ Raw triples toggle                        │
├───────────────────────────┴──────────────────────────────────────────┤
│ Contributors • baseline • validation summary     [Reject] [Approve] │
└──────────────────────────────────────────────────────────────────────┘
```

## 14.2 Explicit Diff

Primary format:

```text
Add class “Commercial Loan”
Set parent to “Loan”
Add definition “A loan issued…”
```

Secondary expandable format:

- Added RDF triples.
- Removed RDF triples.
- Source file.

## 14.3 Reasoning Impact

Sections:

- New inferences.
- Removed inferences.
- Consistency.
- Unsatisfiable classes.
- Incomplete/unsupported status.

Use purple inferred markers.

## 14.4 SHACL Impact

Sections:

- New violations.
- Worsened.
- Resolved.
- Unchanged existing.
- Warnings.
- Information.

Use the existing baseline-aware policy.

## 14.5 Sticky Review Footer

Show:

- Number of staged changes.
- Contributors.
- Affected sources.
- Blocking issues.
- Reviewer permission.

Actions:

- `Reject`
- `Approve & apply`

Disable approval with a specific reason.

Do not use a disabled button without explanation.

---

# 15. Reasoning Workspace

## 15.1 Summary Header

Show:

- Status.
- Target: applied graph or preview.
- Fingerprint abbreviation.
- Started/completed time.
- Reasoner.
- `Run again`.
- `Cancel`, when running.

## 15.2 Summary Metrics

Use compact metric blocks:

- Consistent / inconsistent.
- Inferred class links.
- Inferred types.
- Unsatisfiable classes.
- Unsupported features.
- Import issues.

Do not use oversized dashboard cards.

## 15.3 Result List

Filters:

- Entity kind.
- Inference type.
- Source.
- Search.

Each row:

```text
Commercial Loan  →  Financial Product
Inferred through Loan
[Explain]
```

## 15.4 Explanation Drawer

Show:

- Conclusion.
- Supporting asserted facts.
- Rule/path.
- Source entities.
- Caveat when a minimal justification is unavailable.

---

# 16. SHACL Workspace

## 16.1 Findings List

Columns:

- Severity.
- Entity.
- Property/path.
- Message.
- Shape.
- State:
  - Current.
  - New.
  - Worsened.
  - Resolved.
  - Stale.

## 16.2 Filters

- Severity.
- Shape.
- Entity type.
- Current/preview.
- Validation mode.

## 16.3 Finding Detail

Right drawer:

- Message.
- Focus node.
- Path.
- Invalid value.
- Shape.
- Constraint type.
- Source.
- Validation mode.
- Related entity action.
- AI explanation action.

No create/edit/delete controls unless supported by an approved typed backend contract.

---

# 17. FIBO Workspace

## 17.1 Layout

```text
┌──────────────────────┬──────────────────────────────┬──────────────────────┐
│ Modules & Filters    │ Search Results               │ Candidate Detail     │
│                      │                              │                      │
│ Curated Foundations  │ Loan Agreement               │ Label                │
│ Agreements           │ Credit Facility              │ Definition           │
│ Parties              │ ...                          │ Parents              │
│ Payments             │                              │ Domain / Range       │
│                      │                              │ Match reasons        │
└──────────────────────┴──────────────────────────────┴──────────────────────┘
```

## 17.2 Search Results

Each result row shows:

- Label.
- Entity kind.
- Module.
- Confidence band.
- One-line definition.
- Top match reason.
- Already-used status.

Do not make the raw numeric score the dominant element.

The full score breakdown is available in detail.

## 17.3 Candidate Detail

Show:

- Preferred label.
- Definition.
- Alternate labels.
- Original IRI.
- Module.
- Maturity.
- Parents.
- Domain/range.
- Match reason breakdown.
- Dependencies.
- Local usage.

Actions:

- `Use directly`
- `Create local subclass`
- `Open source details`

## 17.4 Dependency Review

Before staging:

- Required dependencies.
- Optional dependencies.
- Already available.
- New import/reference.
- Maturity acknowledgement.
- Reason for each dependency.

Primary action:

- `Preview & stage reuse`

FIBO assets remain immutable.

---

# 18. AI Assistant

## 18.1 Placement

The AI assistant opens in the right drawer from any workspace.

It may also have a full tab for longer conversations.

## 18.2 Header

Show:

- `Entio AI`.
- Credential status.
- Current context.
- New conversation.
- Close.

Context chips:

- Current entity.
- Proposal preview.
- SHACL finding.
- FIBO candidate.
- Reasoning result.

Users can remove context chips before sending.

## 18.3 Empty State

Without key:

```text
Connect your OpenAI API key to use Entio AI.
Your key is stored only for your current server session.
[Open AI settings]
```

Non-AI features remain available.

With key:

Suggested prompts:

- Explain this entity.
- Explain this inference.
- Explain this SHACL violation.
- Find a FIBO concept.
- Suggest a definition.
- Suggest a superclass.
- Summarize staged changes.

## 18.4 Response Anatomy

```text
Answer
Evidence
- Asserted fact
- Inferred fact
- FIBO result

Suggested change
[Add superclass: Commercial Loan → Loan]
[Preview & stage]
```

Every response visually separates:

- Narrative.
- Evidence.
- Inferred facts.
- FIBO results.
- Suggestions.
- Uncertainty.
- Warnings.

## 18.5 Suggestions

Only supported typed suggestions receive a stage action.

The button must say:

- `Preview & stage`

Never:

- `Apply`
- `Fix automatically`
- `Accept all`

AI-generated staged changes receive the AI marker.

---

# 19. Collaboration UI

## 19.1 Presence

Header avatar group:

- Show up to four users.
- Overflow count after four.
- Tooltip lists users and roles.
- Small green online indicator.

## 19.2 Entity Activity

Tree row or entity header may show one small avatar when another user is actively viewing or editing.

Tooltip:

```text
Maya is editing this class
```

Do not show multiple floating cursors.

## 19.3 Activity Timeline

Events:

- User joined.
- Entity opened.
- Change staged.
- Change edited.
- Conflict detected.
- Proposal reviewed.
- Proposal applied.
- Reasoning completed.
- SHACL completed.
- AI suggestion staged.

Use concise natural language.

## 19.4 Disconnected State

Persistent top banner:

```text
Connection lost. Viewing cached state.
Changes cannot be staged until the connection is restored.
[Reconnect]
```

Do not allow ambiguous offline mutation.

---

# 20. Settings

Sections:

- Profile.
- Appearance.
- AI credential.
- Workspace.
- Keyboard shortcuts.
- About.

## 20.1 AI Credential

States:

- Not configured.
- Testing.
- Configured.
- Invalid.
- Revoked.
- Rate limited.

Actions:

- Add.
- Replace.
- Test.
- Remove.

Never reveal the stored key after submission.

## 20.2 Appearance

Phase 6 minimum:

- Comfortable / compact density toggle.
- Reduce motion.
- Theme-ready architecture.

Dark mode may be deferred.

---

# 21. Empty, Loading, Error, And Permission States

Every major view must define:

## 21.1 Loading

Use skeletons matching final layout.

Do not use full-screen spinners for local panel loads.

## 21.2 Empty

Explain:

- What the view contains.
- Why it is empty.
- What action is available.

Example:

```text
No staged changes
Draft an ontology change or stage an AI suggestion to begin review.
```

## 21.3 Error

Show:

- Human-readable summary.
- Technical detail disclosure.
- Retry action.
- Correlation/request ID where available.

## 21.4 Permission

Show why the user cannot perform an action.

Example:

```text
Only reviewers can approve and apply proposals.
```

## 21.5 Stale

Show:

- What became stale.
- Which graph it belonged to.
- Refresh/re-prepare action.

---

# 22. Accessibility Requirements

Must meet WCAG 2.1 AA intent.

Required:

- Keyboard access to all major workflows.
- Visible focus ring.
- No color-only status.
- `aria-live` for background job completion and conflicts.
- Proper tree semantics.
- Proper tab semantics.
- Accessible names for icon buttons.
- Tooltips available on focus.
- Escape closes transient overlays.
- Focus returns to trigger after drawer/dialog close.
- Minimum 44px pointer target for touch where feasible, while visual controls may remain compact.
- Reduced-motion support.
- Contrast validation for all semantic colors.

## 22.1 Keyboard Shortcuts

Required:

- `Cmd/Ctrl+K`: command/search.
- `Cmd/Ctrl+P`: project/entity quick open, if not conflicting with browser behavior.
- `Cmd/Ctrl+Enter`: preview and stage current valid draft.
- `Cmd/Ctrl+Shift+R`: open proposal review.
- `Cmd/Ctrl+W`: close current tab.
- `Alt+1…8`: switch app-rail module where browser/platform allows.
- `/`: focus current sidebar search when not typing.
- Escape: close topmost transient panel.

Show shortcuts in tooltips and Settings.

---

# 23. Performance Requirements

## 23.1 Hierarchy

- Virtualized.
- Lazy-loaded.
- No full tree on initial project load.
- Cache loaded children.
- Abort obsolete requests.

## 23.2 Large Lists

Virtualize when rows exceed 100.

Applies to:

- Hierarchy.
- Search results.
- SHACL findings.
- Reasoning results.
- Activity.
- RDF statements.

## 23.3 Perceived Performance

- Immediate selected-row feedback.
- Skeletons within 100ms when data is not ready.
- Preserve prior valid data while background refresh runs.
- Show stale marker rather than blanking the interface.
- Avoid layout shifts.

---

# 24. Frontend File Organization

Recommended structure:

```text
web-app/src/
  app/
    App.tsx
    router.tsx
    providers.tsx
    queryClient.ts
    workspaceStore.ts

  components/
    ui/
      Button/
      Badge/
      Drawer/
      Tabs/
      Tooltip/
      DataTable/
      EmptyState/
      Skeleton/
      ...

    layout/
      AppShell.tsx
      GlobalHeader.tsx
      AppRail.tsx
      ContextSidebar.tsx
      WorkspaceTabs.tsx
      InspectorDrawer.tsx
      BottomDock.tsx

  features/
    projects/
    ontology/
      components/
      routes/
      hooks/
      types/
    entities/
    changes/
    reasoning/
    shacl/
    fibo/
    collaboration/
    ai/
    activity/
    settings/

  api/
    httpClient.ts
    websocketClient.ts
    contracts/
    errors.ts

  styles/
    tokens.css
    globals.css
    typography.css
    utilities.css

  test/
    fixtures/
    renderWithProviders.tsx
```

Rules:

- Feature code does not import from another feature's private internals.
- Reusable visual primitives live in `components/ui`.
- Server state uses feature hooks backed by TanStack Query.
- Browser-local tab/layout/draft state uses the workspace store.
- No RDF, OWL, SHACL, or semantic ranking logic in the frontend.

---

# 25. Component Implementation Order

Implement in this order:

1. Tokens and typography.
2. Primitive controls.
3. App shell and resizable panes.
4. App rail and global header.
5. Context sidebar.
6. Virtualized ontology tree.
7. Tab system.
8. Entity header and overview.
9. Relationship lists.
10. Edit drawer.
11. Shared staged-change dock.
12. Proposal review workspace.
13. Reasoning workspace.
14. SHACL workspace.
15. FIBO workspace.
16. Collaboration indicators.
17. AI drawer.
18. Settings.
19. Accessibility pass.
20. Visual regression and end-to-end tests.

Do not begin screen-specific visual tweaks before the token and primitive layers exist.

---

# 26. Anti-Patterns Codex Must Avoid

Do not implement:

- Large colorful dashboard cards for every data point.
- Thick borders around every section.
- Gradients as the primary visual identity.
- Full IRIs in tree rows.
- Raw RDF as the default entity view.
- Modal dialogs for ordinary editing.
- Separate unrelated pages for every entity subtype.
- An AI chat box that ignores current context.
- AI actions that skip staging.
- Hidden status behind hover only.
- Tiny unlabeled icon buttons.
- Color-only asserted/inferred distinction.
- Persistent empty right panels.
- Fixed unresizable sidebars.
- Loading spinners that replace the entire page.
- Automatic graph layout as the primary navigation model.
- Custom ontology logic in React.
- Fake sample values when a backend field is unavailable.
- Silent fallback from a failed backend request to stale data without a stale marker.

---

# 27. Required Screen Acceptance Criteria

## 27.1 Explore Workspace

- Hierarchy is visible within one second after project summary is available.
- Labels are primary.
- Inferred links are clearly marked.
- Entity opens in a tab.
- Technical IRI is no more than one click away.
- Tree is keyboard accessible.
- No full tree is loaded initially.

## 27.2 Entity Detail

- Definition and key relationships are visible without scrolling excessively at 1440×900.
- Asserted and inferred relationships are not mixed without labels.
- Edit opens in a drawer.
- Related entities are clickable.
- RDF is available but not default.

## 27.3 Stage Flow

- Draft is visibly local before staging.
- `Preview & stage` is the primary action.
- Validation errors preserve input.
- Success adds a shared staged row.
- The source is not described as changed.

## 27.4 Proposal Review

- Explicit, reasoning, and SHACL impact are separate.
- Blocking issues are obvious.
- Approve/apply is only available to reviewers.
- Disabled approval explains why.
- Raw triples are secondary.

## 27.5 Collaboration

- Two users see each other.
- Staged rows show authors.
- Conflict appears in both sessions.
- Reconnect refreshes authoritative state.
- No secret appears in events.

## 27.6 AI

- Missing key has a clear settings path.
- Current context is visible.
- Evidence is separated from suggestions.
- Suggestion uses `Preview & stage`.
- No direct apply action exists.

## 27.7 FIBO

- Search result labels, definitions, and top match reason are visible.
- Full score details are available but not dominant.
- Original IRI is visible.
- Dependency review happens before staging.
- FIBO is clearly read-only but reusable.

---

# 28. Visual Quality Gate

Codex must not declare the redesign complete solely because the feature tests pass.

Before completion:

1. Run the application at 1440×900.
2. Capture screenshots of:
   - Explore hierarchy.
   - Class detail.
   - Edit drawer.
   - Staged changes.
   - Proposal review.
   - Reasoning.
   - SHACL findings.
   - FIBO search.
   - AI assistant.
   - Collaboration with two users.
3. Review every screenshot for:
   - Alignment.
   - Spacing consistency.
   - Text truncation.
   - Empty regions.
   - Excessive borders.
   - Status ambiguity.
   - Focus visibility.
   - Overflow.
4. Fix visual defects before completion.
5. Run Playwright at:
   - 1440×900.
   - 1280×800.
   - 1024×768.

No screenshot should contain:

- Overlapping content.
- Unlabeled status.
- Clipped primary actions.
- Unbounded horizontal scrolling.
- Raw JSON.
- Placeholder text presented as real data.
- Debug UI.
- Browser console errors.

---

# 29. Testing Requirements

## 29.1 Component Tests

Test:

- Tree keyboard behavior.
- Tabs.
- Entity sections.
- Edit drawer.
- Staged rows.
- Proposal impact tabs.
- Status badges.
- AI suggestion card.
- FIBO candidate detail.
- Empty/error/stale/conflict states.

## 29.2 Accessibility Tests

Use automated checks plus keyboard tests.

Required:

- Axe checks for primary routes.
- Tree semantics.
- Tab semantics.
- Drawer focus trap.
- Dialog focus restoration.
- Live-region announcements.
- Contrast checks for semantic badges.

## 29.3 Visual Regression

Use Playwright screenshot comparisons for the required screen set.

Use deterministic fixtures.

Mask:

- Timestamps.
- Random avatar colors.
- Dynamic job durations.

Do not mask layout defects.

## 29.4 End-To-End Journey

Required journey:

```text
Open project
→ expand hierarchy
→ open class
→ open edit drawer
→ preview and stage change
→ second user sees staged change
→ open proposal review
→ run preview reasoning
→ inspect SHACL impact
→ approve and apply
→ both clients refresh
→ open FIBO
→ stage external reuse
→ open AI assistant
→ stage supported AI suggestion
```

---

# 30. Migration Strategy From Current UI

Codex must redesign the frontend without changing backend semantics.

Required steps:

1. Run the current UI.
2. Capture baseline screenshots.
3. Inventory existing routes, components, API hooks, and tests.
4. Identify reusable data hooks and transport clients.
5. Introduce tokens and primitives.
6. Build the new shell behind the existing routes or a temporary frontend-only feature flag.
7. Move one screen at a time.
8. Preserve existing API clients where compatible.
9. Delete obsolete components only after replacement tests pass.
10. Remove the temporary flag after all required journeys use the new UI.

Do not maintain two semantic workflows.

A temporary visual-shell flag is acceptable.

A second proposal implementation is not.

---

# 31. Codex Stop Conditions

Stop and ask before continuing if:

- A requested design requires backend semantic logic in TypeScript.
- An entity detail field is unavailable from existing contracts.
- The redesign would change proposal semantics.
- The redesign would alter reasoning or SHACL policy.
- FIBO result order would need frontend re-ranking.
- A direct apply action would bypass staging.
- The current backend cannot support a required status.
- A new dependency duplicates an existing approved package.
- Existing VS Code or CLI tests fail.
- The UI cannot distinguish stale from current semantic results.
- The current design can only be achieved by exposing API keys to the browser.
- A screen can only be populated with fabricated placeholder data.

---

# 32. Definition Of Done

The redesign is complete when:

- The app uses the specified shell, navigation, and pane structure.
- The visual token system is implemented.
- Core primitives are reusable and accessible.
- The ontology tree is lazy, virtualized, and keyboard accessible.
- Entity details use progressive disclosure.
- IRIs and RDF are available but not dominant.
- Editing uses the right drawer and `Preview & stage`.
- Staged changes are shared and author-attributed.
- Proposal review separates explicit, reasoning, and SHACL impact.
- Reasoning and SHACL results show current, preview, and stale context.
- FIBO browsing is integrated and visually coherent.
- AI is contextual, evidence-based, and stages typed suggestions only.
- Collaboration is visible without overwhelming ontology content.
- Required empty, loading, error, stale, conflict, and permission states exist.
- Screens pass the visual quality gate.
- Playwright journeys and screenshot checks pass.
- Existing Phase 1 through Phase 6 backend, CLI, and VS Code regression tests continue to pass.
- No backend semantic behavior was removed or altered.
