import { type DragEvent, type KeyboardEvent, type MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import EntityDetails from "./EntityDetails";
import HierarchyNode from "./HierarchyNode";
import StagingPanel from "./StagingPanel";
import CollaborationPresence from "./CollaborationPresence";
import SemanticJobPanel from "./SemanticJobPanel";
import ExternalOntologyPanel from "./ExternalOntologyPanel";
import AiAssistantPanel from "./AiAssistantPanel";
import AiCredentialSettings from "./AiCredentialSettings";
import ProfileSettings from "./ProfileSettings";
import Icon from "../components/ui/Icon";
import StatusBadge from "../components/ui/StatusBadge";
import { useHierarchy, useProjectOutline, useProjectSearch, useProjectSummary, useStagedChanges } from "../web/queries";
import type { WebEntityDetailResponse, WebEntityReference, WebHierarchyItem, WebOutlineItem, WebStagedEntry } from "../web/projectApi";
import { entityKindPresentation } from "./entityKindPresentation";
import {
  ContextMenu,
  ContextualEditDialog,
  type ContextMenuState,
  type ContextualEditor,
} from "./ContextualEditing";
import type { WebStagingEditType } from "./stagingEditTypes";

type ModuleId = "explore" | "changes" | "reasoning" | "constraints" | "fibo" | "activity" | "settings";
type RailItemId = ModuleId | "assistant";
type OutlineTabId = "classes" | "objects" | "properties";

interface EntityTab extends WebEntityReference {
  openedAt: number;
}

interface OpenEditor {
  sourceId: string;
  editor: ContextualEditor;
}

interface StagedEntityReference extends WebEntityReference {
  kind: string;
  sourceId: string;
}

const modules: Array<{ id: ModuleId; label: string; icon: Parameters<typeof Icon>[0]["name"] }> = [
  { id: "explore", label: "Explore", icon: "explore" },
  { id: "changes", label: "Proposal", icon: "changes" },
  { id: "reasoning", label: "Reasoning", icon: "reasoning" },
  { id: "constraints", label: "Constraints", icon: "constraints" },
  { id: "fibo", label: "FIBO", icon: "fibo" },
  { id: "activity", label: "Activity", icon: "activity" },
  { id: "settings", label: "Settings", icon: "settings" },
];

const railItems: Array<{ id: RailItemId; label: string; icon: Parameters<typeof Icon>[0]["name"] }> = [
  ...modules.slice(0, -1),
  { id: "assistant", label: "Assistant", icon: "assistant" },
  modules.at(-1)!,
];

const DISPLAY_NAME_STORAGE_KEY = "entio.displayName";
const DEFAULT_DISPLAY_NAME = "Alice Contributor";
let fallbackDisplayName: string | null = null;

function loadDisplayName(): string {
  try {
    if (typeof window.localStorage?.getItem === "function") {
      const storedName = window.localStorage.getItem(DISPLAY_NAME_STORAGE_KEY)?.trim();
      if (storedName) return storedName;
    }
  } catch {
    // Some embedded browsers disable storage; the local preference still works for this session.
  }
  return fallbackDisplayName || DEFAULT_DISPLAY_NAME;
}

function persistDisplayName(displayName: string) {
  fallbackDisplayName = displayName;
  try {
    if (typeof window.localStorage?.setItem === "function") {
      window.localStorage.setItem(DISPLAY_NAME_STORAGE_KEY, displayName);
    }
  } catch {
    // Keep the in-memory preference when persistent browser storage is unavailable.
  }
}

export default function ProjectWorkspace() {
  const { projectId = "" } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tabs, setTabs] = useState<EntityTab[]>([]);
  const [activeModule, setActiveModule] = useState<ModuleId>("explore");
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [railExpanded, setRailExpanded] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [displayName, setDisplayName] = useState(loadDisplayName);
  const [semanticJobIds, setSemanticJobIds] = useState<Record<"reasoning" | "shacl", string | null>>({ reasoning: null, shacl: null });
  const [sidebarWidth, setSidebarWidth] = useState(296);
  const resizingSidebar = useRef(false);
  const [searchInput, setSearchInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const [outlineTab, setOutlineTab] = useState<OutlineTabId>("classes");
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [openEditor, setOpenEditor] = useState<OpenEditor | null>(null);
  const [clipboardMessage, setClipboardMessage] = useState<string | null>(null);
  const [draggedTabIri, setDraggedTabIri] = useState<string | null>(null);
  const [tabOrderMessage, setTabOrderMessage] = useState<string | null>(null);
  const summary = useProjectSummary(projectId);
  const sourceId = summary.data?.sources.find((source) => source.roles.includes("ontology"))?.id ?? summary.data?.sources[0]?.id;
  const shapesSourceId = summary.data?.sources.find((source) => source.roles.map((role) => role.toLowerCase()).includes("shapes"))?.id;
  const rootHierarchy = useHierarchy(projectId, sourceId);
  const outline = useProjectOutline(projectId, sourceId);
  const search = useProjectSearch(projectId, searchText);
  const staged = useStagedChanges(projectId);
  const activeIri = searchParams.get("iri");
  const activeTab = useMemo(() => tabs.find((tab) => tab.iri === activeIri), [tabs, activeIri]);
  const stagedEntries = staged.data?.entries ?? [];
  const proposalStatus = staged.data?.proposal?.status;
  const stagedIsPendingReview = proposalStatus !== "APPROVED" && proposalStatus !== "APPLIED";
  const stagedIris = useMemo(() => stagedEntityIris(stagedEntries), [stagedEntries]);
  const stagedCreated = useMemo(() => proposalStatus === "APPLIED" ? [] : stagedCreatedEntities(stagedEntries), [proposalStatus, stagedEntries]);
  const stagedClassHierarchy = useMemo(() => proposalStatus === "APPLIED" ? emptyStagedClassHierarchy() : buildStagedClassHierarchy(stagedEntries), [proposalStatus, stagedEntries]);
  const stagedDetails = useMemo(() => proposalStatus === "APPLIED" ? new Map<string, WebEntityDetailResponse>() : buildStagedEntityDetails(stagedEntries), [proposalStatus, stagedEntries]);

  useEffect(() => {
    const normalizedSearch = searchInput.trim();
    if (!normalizedSearch) {
      setSearchText("");
      return;
    }
    const timer = window.setTimeout(() => setSearchText(normalizedSearch), 200);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    if (!clipboardMessage) return undefined;
    const timeout = window.setTimeout(() => setClipboardMessage(null), 3_000);
    return () => window.clearTimeout(timeout);
  }, [clipboardMessage]);

  useEffect(() => {
    function resize(event: PointerEvent) {
      if (!resizingSidebar.current) return;
      setSidebarWidth(Math.min(420, Math.max(240, event.clientX - 64)));
    }
    function stopResize() { resizingSidebar.current = false; }
    window.addEventListener("pointermove", resize);
    window.addEventListener("pointerup", stopResize);
    return () => {
      window.removeEventListener("pointermove", resize);
      window.removeEventListener("pointerup", stopResize);
    };
  }, []);

  function openEntity(entity: WebEntityReference) {
    setTabs((existing) => existing.some((tab) => tab.iri === entity.iri) ? existing : [...existing, { ...entity, openedAt: Date.now() }]);
    setActiveModule("explore");
    navigate(`/projects/${encodeURIComponent(projectId)}?iri=${encodeURIComponent(entity.iri)}`);
  }

  function closeTab(iri: string) {
    const remaining = tabs.filter((tab) => tab.iri !== iri);
    setTabs(remaining);
    if (iri === activeIri) {
      const next = remaining.at(-1);
      navigate(next ? `/projects/${encodeURIComponent(projectId)}?iri=${encodeURIComponent(next.iri)}` : `/projects/${encodeURIComponent(projectId)}`);
    }
  }

  function moveTab(iri: string, targetIri: string) {
    if (iri === targetIri) return;
    setTabs((current) => reorderTabs(current, iri, targetIri));
    const moved = tabs.find((tab) => tab.iri === iri);
    if (moved) setTabOrderMessage(`${moved.label} tab moved.`);
  }

  function moveTabByKeyboard(event: KeyboardEvent<HTMLButtonElement>, tab: EntityTab) {
    if (!event.altKey || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
    event.preventDefault();
    const currentIndex = tabs.findIndex((candidate) => candidate.iri === tab.iri);
    const nextIndex = Math.min(tabs.length - 1, Math.max(0, currentIndex + (event.key === "ArrowRight" ? 1 : -1)));
    if (nextIndex === currentIndex) return;
    setTabs((current) => {
      const next = [...current];
      const [moved] = next.splice(currentIndex, 1);
      next.splice(nextIndex, 0, moved);
      return next;
    });
    setTabOrderMessage(`${tab.label} tab moved to position ${nextIndex + 1}.`);
  }

  function startTabDrag(event: DragEvent<HTMLDivElement>, tab: EntityTab) {
    setDraggedTabIri(tab.iri);
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", tab.iri);
  }

  function openModule(moduleId: ModuleId) {
    setAccountOpen(false);
    setActiveModule(moduleId);
  }

  function saveDisplayName(nextDisplayName: string) {
    persistDisplayName(nextDisplayName);
    setDisplayName(nextDisplayName);
  }

  function launchEditor(editor: ContextualEditor, targetSourceId = sourceId) {
    if (!targetSourceId) return;
    setContextMenu(null);
    setOpenEditor({ sourceId: targetSourceId, editor });
  }

  function openRootContextMenu(event: MouseEvent) {
    event.preventDefault();
    setContextMenu(outlineContextMenu(event.clientX, event.clientY, outlineTab, (editor) => launchEditor(editor)));
  }

  function openEntityContextMenu(event: MouseEvent, entity: WebEntityReference) {
    event.preventDefault();
    event.stopPropagation();
    setContextMenu(entityContextMenu(
      event.clientX,
      event.clientY,
      entity,
      (editor) => launchEditor(editor, entity.sourceId ?? sourceId),
      () => openEntity(entity),
      () => void copyEntityIri(entity),
    ));
  }

  async function copyEntityIri(entity: WebEntityReference) {
    try {
      await navigator.clipboard.writeText(entity.iri);
      setClipboardMessage(`${entity.label} IRI copied.`);
    } catch {
      setClipboardMessage("Could not copy the IRI.");
    }
  }

  if (summary.isPending) return <main className="app-shell state-page"><p role="status">Loading project...</p></main>;
  if (summary.isError) return <main className="app-shell state-page"><p role="alert">Could not load project. {summary.error.message}</p></main>;
  const project = summary.data.project;
  const module = modules.find((item) => item.id === activeModule) ?? modules[0];
  const stagedCount = staged.data?.entries?.length ?? 0;
  const outlineItems = outline.data?.page.items ?? [];
  const createdClasses = stagedCreated.filter((item) => item.kind === "Class");
  const createdObjects = stagedCreated.filter((item) => item.kind === "Individual");
  const createdProperties = stagedCreated.filter((item) => item.kind?.endsWith("Property"));
  const classCount = outlineItems.filter((item) => item.kind === "Class").length + createdClasses.length;
  const objects = outlineItems.filter((item) => item.kind === "Individual");
  const properties = outlineItems.filter((item) => item.kind.endsWith("Property"));
  const objectItems = mergeOutlineEntities(objects, createdObjects);
  const propertyItems = mergeOutlineEntities(properties, createdProperties);
  const rootClassItems = [
    ...(rootHierarchy.data?.page.items ?? []).filter((item) => !stagedClassHierarchy.childIris.has(item.iri)),
    ...stagedClassHierarchy.rootItems.filter((stagedItem) => !(rootHierarchy.data?.page.items ?? []).some((item) => item.iri === stagedItem.iri)),
  ].sort((left, right) => left.label.localeCompare(right.label) || left.iri.localeCompare(right.iri));

  function adjustSidebar(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      setSidebarWidth((value) => Math.min(420, Math.max(240, value + (event.key === "ArrowRight" ? 16 : -16))));
    }
  }

  return (
    <main className="workbench-shell">
      <a className="skip-link" href="#entity-workspace-panel">Skip to workspace</a>
      <header className="global-header">
        <Link className="brand-lockup" to="/" aria-label="Entio home"><span className="brand-mark">E</span><span>ENTIO</span></Link>
        <div className="project-context">
          <span className="project-context-label">Project</span>
          <h1>{project.name}</h1>
          <StatusBadge tone="success">Connected</StatusBadge>
        </div>
        <div className="header-actions">
          <CollaborationPresence projectId={projectId} activeEntityIri={activeIri} />
          <Link className="quiet-link" to="/">Projects</Link>
        </div>
      </header>
      <div className="workbench-layout" style={{ gridTemplateColumns: `${railExpanded ? "206px" : "56px"} ${sidebarWidth}px minmax(0, 1fr)` }}>
        <nav className={`app-rail ${railExpanded ? "app-rail-expanded" : ""}`} aria-label="Workbench modules">
          <div className="rail-top"><button className="rail-toggle" type="button" aria-label={railExpanded ? "Collapse navigation" : "Expand navigation"} aria-expanded={railExpanded} onClick={() => setRailExpanded((value) => !value)}><span className="rail-logo" aria-hidden="true">E</span><span className="rail-toggle-label">Entio</span></button></div>
          <div className="rail-modules" aria-label="Workbench navigation">
            {railItems.map((item) => {
              const isAssistant = item.id === "assistant";
              const selected = isAssistant ? assistantOpen : activeModule === item.id;
              return <button key={item.id} type="button" role={isAssistant ? undefined : "tab"} className={`rail-button ${selected ? "rail-button-active" : ""}`} aria-label={item.label} title={item.label} aria-selected={isAssistant ? undefined : selected} aria-expanded={isAssistant ? assistantOpen : undefined} aria-controls={isAssistant ? "assistant-drawer" : "entity-workspace-panel"} onClick={() => isAssistant ? setAssistantOpen((value) => !value) : openModule(item.id as ModuleId)}><Icon name={item.icon} /><span className="rail-label">{item.label}</span>{item.id === "changes" && stagedCount ? <span className={`rail-count ${stagedIsPendingReview ? "staged-pending" : ""}`}>{stagedCount}</span> : null}</button>;
            })}
          </div>
          <div className="rail-spacer" />
          <div className="rail-account">
            <button className={`rail-button account-button ${accountOpen ? "rail-button-active" : ""}`} type="button" aria-label="Account" aria-expanded={accountOpen} onClick={() => setAccountOpen((value) => !value)}><Icon name="account" /><span className="rail-label">Account</span></button>
            {accountOpen ? <div className="account-menu" role="dialog" aria-label="Account menu"><div className="account-menu-header"><span className="overline">Account</span><strong>{displayName}</strong><span className="muted">Local reviewer</span></div><button className="button" type="button" onClick={() => openModule("settings")}>Open settings</button></div> : null}
          </div>
        </nav>
        <aside className="sidebar" aria-label="Project navigation">
          <div className="sidebar-header"><div><span className="overline">Navigate</span><h2>Ontology</h2></div><StatusBadge tone="asserted">{summary.data.symbolCount + stagedCreated.length} symbols</StatusBadge></div>
          <section className="sidebar-section sidebar-search" aria-labelledby="search-heading">
            <div role="search">
              <label className="visually-hidden" id="search-heading" htmlFor="entity-search">Search entities by label</label>
              <div className="search-row"><Icon name="search" /><input id="entity-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Search entities" /></div>
            </div>
          </section>
          {searchInput.trim() ? <section className="sidebar-section sidebar-search-results" aria-labelledby="search-results-heading">
            <div className="section-heading compact"><h3 id="search-results-heading">Search results</h3><button className="icon-button" type="button" aria-label="Clear search" onClick={() => { setSearchInput(""); setSearchText(""); }}><Icon name="close" /></button></div>
            <SearchResults query={search} onOpen={openEntity} />
          </section> : <>
            <section className="sidebar-section hierarchy-section" aria-labelledby="hierarchy-heading">
              <div className="section-heading compact"><h3 id="hierarchy-heading">Project outline</h3><div className="outline-heading-actions">{outlineTab === "classes" && rootHierarchy.isFetching ? <span className="status-text">Refreshing</span> : null}<button className="icon-button" type="button" aria-label={`Add ${outlineTab === "classes" ? "class" : outlineTab === "objects" ? "individual" : "property"}`} onClick={(event) => setContextMenu(outlineContextMenu(event.currentTarget.getBoundingClientRect().right, event.currentTarget.getBoundingClientRect().bottom, outlineTab, (editor) => launchEditor(editor)))}>+</button></div></div>
              <div className="outline-tabs" role="tablist" aria-label="Project outline sections">
                <OutlineTab id="classes" label="Classes" count={classCount} active={outlineTab} onSelect={setOutlineTab} />
                <OutlineTab id="objects" label="Objects" count={objectItems.length} active={outlineTab} onSelect={setOutlineTab} />
                <OutlineTab id="properties" label="Properties" count={propertyItems.length} active={outlineTab} onSelect={setOutlineTab} />
              </div>
              <div className="hierarchy-viewport" id={`outline-panel-${outlineTab}`} role="tabpanel" aria-labelledby={`outline-tab-${outlineTab}`} onContextMenu={openRootContextMenu}>
                {outlineTab === "classes" ? <>
                  {rootHierarchy.isPending ? <p role="status">Loading hierarchy...</p> : null}
                  {rootHierarchy.isError ? <p role="alert">Hierarchy unavailable.</p> : null}
                  {rootClassItems.length ? <ul className="hierarchy-list">{rootClassItems.map((item) => <HierarchyNode key={`${item.sourceId}:${item.iri}`} projectId={projectId} item={item} depth={0} onOpen={openEntity} onContextMenu={openEntityContextMenu} stagedIris={stagedIsPendingReview ? stagedIris : undefined} stagedChildrenByParent={stagedIsPendingReview ? stagedClassHierarchy.childrenByParent : undefined} />)}</ul> : null}
                </> : null}
                {outlineTab === "objects" ? <OutlineEntityList title="Objects" marker="O" items={objectItems} loading={outline.isPending} error={outline.isError} onOpen={openEntity} onContextMenu={openEntityContextMenu} stagedIris={stagedIsPendingReview ? stagedIris : undefined} /> : null}
                {outlineTab === "properties" ? <OutlineEntityList title="Properties" marker="P" items={propertyItems} loading={outline.isPending} error={outline.isError} onOpen={openEntity} onContextMenu={openEntityContextMenu} stagedIris={stagedIsPendingReview ? stagedIris : undefined} /> : null}
                {outlineTab !== "classes" && outline.data?.page.nextOffset != null ? <p className="outline-limit-note">Showing the first {outline.data.page.items.length} of {outline.data.page.total} symbols.</p> : null}
              </div>
            </section>
            <details className="sidebar-sources">
              <summary><span>Sources</span><span className="count-label">{summary.data.sources.length}</span></summary>
              <ul className="source-list">{summary.data.sources.map((source) => <li key={source.id}><strong>{source.id}</strong><small>{source.path}</small></li>)}</ul>
            </details>
          </>}
          <div className="sidebar-footer"><span>Graph statements</span><strong>{summary.data.graphTripleCount}</strong></div>
          <button type="button" className="pane-resizer" role="separator" aria-label="Resize ontology sidebar" aria-orientation="vertical" aria-valuemin={240} aria-valuemax={420} aria-valuenow={sidebarWidth} title="Resize ontology sidebar" onPointerDown={() => { resizingSidebar.current = true; }} onKeyDown={adjustSidebar} />
        </aside>
        <section className={`workspace-pane ${assistantOpen ? "workspace-pane-assistant-open" : ""}`} aria-label="Entity workspace">
          <div className="workspace-main">
            <div className="workspace-toolbar"><div><span className="overline">{module.label}</span><span className="toolbar-title">{activeModule === "explore" ? activeTab?.label ?? "Semantic workspace" : module.label}</span></div><div className="toolbar-stats"><span>{summary.data.graphTripleCount} statements</span><StatusBadge tone={stagedCount && stagedIsPendingReview ? "staged" : "neutral"}>{stagedCount} staged</StatusBadge></div></div>
            {activeModule === "explore" && tabs.length ? <>
              <nav className="entity-tabs" aria-label="Open entities" role="tablist">{tabs.map((tab) => <div
                className={`entity-tab ${stagedIsPendingReview && stagedIris.has(tab.iri) ? "entity-staged" : ""} ${draggedTabIri === tab.iri ? "entity-tab-dragging" : ""}`}
                key={tab.iri}
                role="presentation"
                draggable
                onDragStart={(event) => startTabDrag(event, tab)}
                onDragEnd={() => setDraggedTabIri(null)}
                onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
                onDrop={(event) => { event.preventDefault(); const sourceIri = draggedTabIri ?? event.dataTransfer.getData("text/plain"); moveTab(sourceIri, tab.iri); setDraggedTabIri(null); }}
              ><button type="button" role="tab" aria-label={tab.label} aria-selected={tab.iri === activeIri} aria-keyshortcuts="Alt+ArrowLeft Alt+ArrowRight" tabIndex={tab.iri === activeIri ? 0 : -1} onClick={() => openEntity(tab)} onKeyDown={(event) => moveTabByKeyboard(event, tab)}>{tab.label}<small>{tab.kind}</small></button><button type="button" className="tab-close" aria-label={`Close ${tab.label}`} onClick={() => closeTab(tab.iri)}><Icon name="close" /></button></div>)}</nav>
              <span className="visually-hidden" role="status" aria-live="polite">{tabOrderMessage}</span>
            </> : null}
            <div className={`workspace-content ${activeModule === "explore" && activeIri && stagedIsPendingReview && stagedIris.has(activeIri) ? "workspace-content-staged" : ""}`} id="entity-workspace-panel" role="tabpanel" aria-label={activeModule === "explore" ? activeTab ? `${activeTab.label} details` : "Entity details" : `${module.label} workspace`} aria-live="polite">
              {renderModule(activeModule, projectId, sourceId, shapesSourceId, activeTab, semanticJobIds, (kind, status) => setSemanticJobIds((current) => ({ ...current, [kind]: status.id })), activeTab ? <EntityDetails projectId={projectId} iri={activeTab.iri} stagedEntity={stagedDetails.get(activeTab.iri)} /> : <EmptyWorkspace />, displayName, saveDisplayName, launchEditor)}
            </div>
            {activeModule !== "changes" ? <div className={`staged-dock ${stagedCount && stagedIsPendingReview ? "staged-dock-pending" : ""}`} aria-label="Shared staged changes"><div><span className="overline">Shared review queue</span><strong>{stagedCount ? `${stagedCount} change${stagedCount === 1 ? "" : "s"} staged` : "No staged changes"}</strong></div><span className="dock-meta">Review the complete proposal, then accept or reject it.</span><button type="button" onClick={() => openModule("changes")}>{stagedCount ? "Review proposal" : "Open proposal"}</button></div> : null}
          </div>
          {assistantOpen ? <aside className="assistant-drawer" id="assistant-drawer" aria-label="Entio AI assistant"><div className="assistant-drawer-header"><div><span className="overline">Assistant</span><h2>Entio AI</h2></div><button className="icon-button" type="button" aria-label="Close assistant" onClick={() => setAssistantOpen(false)}><Icon name="close" /></button></div><AiAssistantPanel projectId={projectId} entity={activeTab ?? null} /></aside> : null}
        </section>
      </div>
      {contextMenu ? <ContextMenu menu={contextMenu} onClose={() => setContextMenu(null)} /> : null}
      {openEditor ? <ContextualEditDialog projectId={projectId} sourceId={openEditor.sourceId} editor={openEditor.editor} onClose={() => setOpenEditor(null)} /> : null}
      {clipboardMessage ? <div className="workflow-toast" role="status" aria-live="polite">{clipboardMessage}</div> : null}
    </main>
  );
}

function renderModule(module: ModuleId, projectId: string, sourceId: string | undefined, shapesSourceId: string | undefined, activeTab: EntityTab | undefined, semanticJobIds: Record<"reasoning" | "shacl", string | null>, onSemanticJobSubmitted: (kind: "reasoning" | "shacl", status: { id: string }) => void, exploreContent: React.ReactNode, displayName: string, onDisplayNameSave: (displayName: string) => void, onOpenEditor: (editor: ContextualEditor, sourceId?: string) => void) {
  if (module === "explore") return <div className="explore-layout"><div className="entity-surface">{exploreContent}</div></div>;
  if (module === "changes") return sourceId ? <div className="module-page proposal-page"><PageIntro eyebrow="Review" title="Proposal" description="Review all staged edits together, then accept and apply them or reject the proposal." /><StagingPanel projectId={projectId} /></div> : <Unavailable />;
  if (module === "reasoning") return <div className="module-page"><PageIntro eyebrow="Semantic status" title="Reasoning" description="Deterministic reasoning against the applied graph or the current proposal." /><SemanticJobPanel projectId={projectId} initialJobId={semanticJobIds.reasoning} onJobSubmitted={(kind, status) => onSemanticJobSubmitted(kind, status)} /></div>;
  if (module === "constraints") return <div className="module-page"><PageIntro eyebrow="Constraint checks" title="SHACL validation" description="Inspect validation status without losing the distinction between asserted graph and proposal graph." />{shapesSourceId ? <ConstraintAuthoring onOpen={(editType) => onOpenEditor({ kind: "typed", editType }, shapesSourceId)} /> : null}<SemanticJobPanel projectId={projectId} initialJobId={semanticJobIds.shacl} onJobSubmitted={(kind, status) => onSemanticJobSubmitted(kind, status)} /></div>;
  if (module === "fibo") return sourceId ? <div className="module-page"><PageIntro eyebrow="External ontology" title="FIBO" description="Browse the pinned, read-only catalog and stage reuse proposals through the shared review queue." /><ExternalOntologyPanel projectId={projectId} sourceId={sourceId} /></div> : <Unavailable />;
  if (module === "activity") return <div className="module-page"><PageIntro eyebrow="Collaboration" title="Activity" description="Quiet presence and activity signals keep shared work understandable." /><CollaborationActivity projectId={projectId} activeEntityIri={activeTab?.iri ?? null} /></div>;
  return <div className="module-page"><PageIntro eyebrow="Workspace" title="Settings" description="Manage your local profile and the optional AI provider credential." /><div className="settings-grid"><ProfileSettings displayName={displayName} onSave={onDisplayNameSave} /><AiCredentialSettings /></div></div>;
}

function PageIntro({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <header className="module-intro"><span className="overline">{eyebrow}</span><h1>{title}</h1><p>{description}</p></header>;
}

function CollaborationActivity({ projectId, activeEntityIri }: { projectId: string; activeEntityIri: string | null }) {
  return <section className="activity-surface"><CollaborationPresence projectId={projectId} activeEntityIri={activeEntityIri} /><p className="muted">Activity is delivered by the collaboration channel and reflects server-authoritative project events.</p></section>;
}

function ConstraintAuthoring({ onOpen }: { onOpen: (editType: WebStagingEditType) => void }) {
  return <section className="constraint-authoring" aria-labelledby="constraint-authoring-heading">
    <div className="section-heading"><div><span className="overline">Shape authoring</span><h2 id="constraint-authoring-heading">Constraints</h2></div></div>
    <div className="button-row">
      <button className="button primary" type="button" onClick={() => onOpen("shacl-create-node-shape")}>Add node shape</button>
      <button className="button" type="button" onClick={() => onOpen("shacl-create-property-shape")}>Add property constraint</button>
      <button className="button" type="button" onClick={() => onOpen("shacl-update-constraint")}>Update constraint</button>
      <button className="button" type="button" onClick={() => onOpen("shacl-remove-constraint")}>Remove constraint</button>
      <button className="button danger" type="button" onClick={() => onOpen("shacl-delete-shape")}>Delete shape</button>
    </div>
  </section>;
}

function Unavailable() { return <div className="empty-state"><h2>Source unavailable</h2><p>This workspace needs an ontology source before it can open.</p></div>; }

function SearchResults({ query, onOpen }: { query: ReturnType<typeof useProjectSearch>; onOpen: (entity: WebEntityReference) => void }) {
  if (query.isPending) return <p role="status">Searching...</p>;
  if (query.isError) return <p role="alert">Search unavailable.</p>;
  if (!query.data?.page.items.length) return <p className="muted">No matching entities.</p>;
  return <ul className="search-results">{query.data.page.items.map((result) => {
    const presentation = entityKindPresentation(result.kind);
    return <li key={`${result.sourceId}:${result.iri}`}><button type="button" onClick={() => onOpen({ iri: result.iri, label: result.label, kind: result.kind, sourceId: result.sourceId })}><span className={`entity-type-marker ${presentation.className}`} aria-hidden="true">{presentation.marker}</span><span><strong>{result.label}</strong><small>{presentation.label} · {searchReasonLabel(result.reason)}</small></span></button></li>;
  })}</ul>;
}

function searchReasonLabel(reason: string): string {
  return reason.replace(/([a-z])([A-Z])/g, "$1 $2");
}

function OutlineTab({ id, label, count, active, onSelect }: { id: OutlineTabId; label: string; count: number; active: OutlineTabId; onSelect: (tab: OutlineTabId) => void }) {
  return <button id={`outline-tab-${id}`} type="button" role="tab" aria-selected={active === id} aria-controls={`outline-panel-${id}`} tabIndex={active === id ? 0 : -1} onClick={() => onSelect(id)}><span>{label}</span><small>{count}</small></button>;
}

function OutlineEntityList({ title, marker, items, loading, error, onOpen, onContextMenu, stagedIris }: { title: string; marker: "O" | "P"; items: WebOutlineItem[]; loading: boolean; error: boolean; onOpen: (entity: WebEntityReference) => void; onContextMenu: (event: MouseEvent, entity: WebEntityReference) => void; stagedIris?: ReadonlySet<string> }) {
  return <section aria-label={title}>
    {error ? <p role="alert">{title} unavailable.</p> : null}
    {loading ? <p className="tree-status" role="status">Loading {title.toLowerCase()}...</p> : null}
    {!loading && !error && !items.length ? <p className="outline-empty">No {title.toLowerCase()}.</p> : null}
    {items.length ? <ul className="outline-entity-list">{items.map((item) => {
      const presentation = entityKindPresentation(item.kind);
      return <li className={stagedIris?.has(item.iri) ? "entity-staged" : undefined} key={`${item.sourceId}:${item.iri}`} onContextMenu={(event) => onContextMenu(event, item)}><button className="entity-link" type="button" aria-label={`${item.label}, ${presentation.label}`} onClick={() => onOpen(item)}><span className={`entity-type-marker ${presentation.className}`} aria-hidden="true">{marker}</span><span className="entity-link-label">{item.label}</span><small>{presentation.label}</small></button></li>;
    })}</ul> : null}
  </section>;
}

function mergeOutlineEntities(applied: WebOutlineItem[], staged: StagedEntityReference[]): WebOutlineItem[] {
  const byIri = new Map<string, WebOutlineItem>();
  [...applied, ...staged].forEach((item) => byIri.set(item.iri, {
    iri: item.iri,
    label: item.label,
    kind: item.kind,
    sourceId: item.sourceId,
  }));
  return [...byIri.values()].sort((left, right) => left.label.localeCompare(right.label) || left.iri.localeCompare(right.iri));
}

function stagedEntityIris(entries: WebStagedEntry[]): Set<string> {
  return new Set(entries.flatMap((entry) => [
    ...entry.generatedIris,
    ...Object.values(entry.normalizedValues).filter((value) => /^https?:\/\//.test(value)),
  ]));
}

function stagedCreatedEntities(entries: WebStagedEntry[]): StagedEntityReference[] {
  const kinds: Record<string, string> = {
    "create-class": "Class",
    "create-individual": "Individual",
    "create-object-property": "ObjectProperty",
    "create-datatype-property": "DatatypeProperty",
  };
  return entries.flatMap((entry) => {
    const editType = entry.summary.split(" · ")[0];
    const kind = kinds[editType];
    const iri = entry.generatedIris[0];
    const label = entry.normalizedValues.label ?? entry.normalizedValues.individualLabel;
    return kind && iri && label ? [{ iri, label, kind, sourceId: entry.sourceId }] : [];
  });
}

function buildStagedEntityDetails(entries: WebStagedEntry[]): Map<string, WebEntityDetailResponse> {
  const details = new Map<string, WebEntityDetailResponse>();
  stagedCreatedEntities(entries).forEach((entity) => details.set(entity.iri, {
    apiVersion: "v1",
    iri: entity.iri,
    label: entity.label,
    kind: entity.kind,
    sourceId: entity.sourceId,
    sourceOntologyId: entity.sourceId,
    locality: "Staged",
    preferredLabelSource: "Staged edit",
    alternateLabels: [],
    definitions: [],
    annotations: [],
    directSuperclasses: [],
    directSubclasses: [],
    directlyTypedIndividuals: [],
    assertedTypes: [],
    domains: [],
    ranges: [],
    outgoingRelationships: [],
    incomingRelationships: [],
  }));

  entries.forEach((entry) => {
    const values = entry.normalizedValues;
    const editType = entry.summary.split(" · ")[0] || entry.editType;
    const targetIri = editType === "create-individual"
      ? values.individualIri
      : values.classIri ?? values.propertyIri ?? values.resourceIri ?? values.targetIri;
    if (!targetIri) return;
    const entity = details.get(targetIri);
    if (!entity) return;
    if (editType === "set-entity-label" && values.label) entity.label = values.label;
    if (editType === "add-superclass" && values.superclassIri) {
      entity.directSuperclasses.push(stagedReference(values.superclassIri, values.superclassLabel, "Class", entry.sourceId));
    }
    if (editType === "remove-superclass" && values.superclassIri) {
      entity.directSuperclasses = entity.directSuperclasses.filter((item) => item.iri !== values.superclassIri);
    }
    const assertedTypeIri = editType === "create-individual" ? values.classIri : values.typeIri;
    const assertedTypeLabel = editType === "create-individual" ? values.classLabel : values.typeLabel;
    if ((editType === "create-individual" || editType === "assign-type") && assertedTypeIri) {
      entity.assertedTypes.push(stagedReference(assertedTypeIri, assertedTypeLabel, "Class", entry.sourceId));
    }
    if (values.domainClassIri) {
      entity.domains.push(stagedReference(values.domainClassIri, values.domainClassLabel, "Class", entry.sourceId));
    }
    if (values.rangeIri) {
      entity.ranges.push(stagedReference(values.rangeIri, values.rangeLabel, null, entry.sourceId));
    }
    if (editType === "add-definition" && values.value) {
      entity.definitions.push({ value: values.value, language: null, datatype: null });
    }
    if (editType === "add-alternate-label" && values.value) {
      entity.alternateLabels.push({ value: values.value, language: null, datatype: null });
    }
  });
  return details;
}

function stagedReference(iri: string, label: string | undefined, kind: string | null, sourceId: string): WebEntityReference {
  return { iri, label: label ?? iriLocalName(iri), kind, sourceId };
}

interface StagedClassHierarchy {
  rootItems: WebHierarchyItem[];
  childIris: Set<string>;
  childrenByParent: Map<string, WebHierarchyItem[]>;
}

function emptyStagedClassHierarchy(): StagedClassHierarchy {
  return { rootItems: [], childIris: new Set(), childrenByParent: new Map() };
}

function buildStagedClassHierarchy(entries: WebStagedEntry[]): StagedClassHierarchy {
  const createdClasses = stagedCreatedEntities(entries).filter((item) => item.kind.toLowerCase() === "class");
  const labels = new Map(createdClasses.map((item) => [item.iri, item.label]));
  const sources = new Map(createdClasses.map((item) => [item.iri, item.sourceId]));

  entries.forEach((entry) => {
    const values = entry.normalizedValues;
    if (values.classIri && values.classLabel) labels.set(values.classIri, values.classLabel);
    if (values.superclassIri && values.superclassLabel) labels.set(values.superclassIri, values.superclassLabel);
    if (values.classIri) sources.set(values.classIri, entry.sourceId);
  });

  const childIris = new Set<string>();
  const childrenByParent = new Map<string, WebHierarchyItem[]>();
  entries.filter((entry) => entry.editType === "add-superclass" || entry.summary.split(" · ")[0] === "add-superclass").forEach((entry) => {
    const classIri = entry.normalizedValues.classIri;
    const parentIri = entry.normalizedValues.superclassIri;
    if (!classIri || !parentIri) return;
    childIris.add(classIri);
    const child: WebHierarchyItem = {
      iri: classIri,
      label: labels.get(classIri) ?? iriLocalName(classIri),
      kind: "Class",
      sourceId: sources.get(classIri) ?? entry.sourceId,
      childCount: 0,
    };
    const siblings = childrenByParent.get(parentIri) ?? [];
    if (!siblings.some((item) => item.iri === child.iri)) childrenByParent.set(parentIri, [...siblings, child]);
  });

  const directChildCounts = new Map<string, number>();
  childrenByParent.forEach((children, parentIri) => directChildCounts.set(parentIri, children.length));
  childrenByParent.forEach((children, parentIri) => {
    childrenByParent.set(parentIri, children.map((child) => ({ ...child, childCount: directChildCounts.get(child.iri) ?? 0 })));
  });

  const rootItems = createdClasses
    .filter((item) => !childIris.has(item.iri))
    .map((item) => ({ ...item, childCount: directChildCounts.get(item.iri) ?? 0 }));
  return { rootItems, childIris, childrenByParent };
}

function iriLocalName(iri: string): string {
  const fragment = iri.split(/[\/#]/).filter(Boolean).at(-1) ?? iri;
  return decodeURIComponent(fragment).replace(/([a-z])([A-Z])/g, "$1 $2");
}

function outlineContextMenu(x: number, y: number, tab: OutlineTabId, open: (editor: ContextualEditor) => void): ContextMenuState {
  const actions = tab === "classes"
    ? [{ label: "Add class", onSelect: () => open({ kind: "class" }) }]
    : tab === "objects"
      ? [{ label: "Add individual", onSelect: () => open({ kind: "typed", editType: "create-individual" }) }]
      : [
          { label: "Add object property", onSelect: () => open({ kind: "typed", editType: "create-object-property" }) },
          { label: "Add datatype property", onSelect: () => open({ kind: "typed", editType: "create-datatype-property" }) },
        ];
  return { x, y, label: `${tab[0].toUpperCase()}${tab.slice(1)} actions`, actions };
}

function entityContextMenu(
  x: number,
  y: number,
  entity: WebEntityReference,
  open: (editor: ContextualEditor) => void,
  openDetails: () => void,
  copyIri: () => void,
): ContextMenuState {
  const kind = entity.kind?.toLowerCase() ?? "";
  const actions: ContextMenuState["actions"] = [
    ...(kind === "class" ? [{ label: "Add subclass", onSelect: () => open({ kind: "class" as const, parent: entity }) }] : []),
    { label: "Edit details", onSelect: openDetails },
    { label: "Copy IRI", onSelect: copyIri },
    { label: "Delete", tone: "danger", onSelect: () => open({ kind: "delete", entity }) },
  ];
  return { x, y, label: `${entity.label} actions`, actions };
}

function EmptyWorkspace() { return <div className="empty-workspace"><span className="overline">Explore</span><h1>Select an entity</h1><p>Choose a class from the outline or search by label to open its details in a tab.</p><div className="empty-hints"><span>Label-first navigation</span><span>Technical details on demand</span></div></div>; }

function reorderTabs(tabs: EntityTab[], sourceIri: string, targetIri: string): EntityTab[] {
  const sourceIndex = tabs.findIndex((tab) => tab.iri === sourceIri);
  const targetIndex = tabs.findIndex((tab) => tab.iri === targetIri);
  if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return tabs;
  const next = [...tabs];
  const [moved] = next.splice(sourceIndex, 1);
  next.splice(targetIndex, 0, moved);
  return next;
}
