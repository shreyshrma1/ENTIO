import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import EntityDetails from "./EntityDetails";
import HierarchyNode from "./HierarchyNode";
import StagingPanel from "./StagingPanel";
import CollaborationPresence from "./CollaborationPresence";
import SemanticJobPanel from "./SemanticJobPanel";
import ExternalOntologyPanel from "./ExternalOntologyPanel";
import AiAssistantPanel from "./AiAssistantPanel";
import AiCredentialSettings from "./AiCredentialSettings";
import Icon from "../components/ui/Icon";
import StatusBadge from "../components/ui/StatusBadge";
import { useHierarchy, useProjectSearch, useProjectSummary, useStagedChanges } from "../web/queries";
import type { WebEntityReference } from "../web/projectApi";

type ModuleId = "explore" | "changes" | "reasoning" | "constraints" | "fibo" | "activity" | "assistant" | "settings";

interface EntityTab extends WebEntityReference {
  openedAt: number;
}

const modules: Array<{ id: ModuleId; label: string; icon: Parameters<typeof Icon>[0]["name"] }> = [
  { id: "explore", label: "Explore", icon: "explore" },
  { id: "changes", label: "Changes", icon: "changes" },
  { id: "reasoning", label: "Reasoning", icon: "reasoning" },
  { id: "constraints", label: "Constraints", icon: "constraints" },
  { id: "fibo", label: "FIBO", icon: "fibo" },
  { id: "activity", label: "Activity", icon: "activity" },
  { id: "assistant", label: "Assistant", icon: "assistant" },
  { id: "settings", label: "Settings", icon: "settings" },
];

export default function ProjectWorkspace() {
  const { projectId = "" } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tabs, setTabs] = useState<EntityTab[]>([]);
  const [activeModule, setActiveModule] = useState<ModuleId>("explore");
  const [railExpanded, setRailExpanded] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [semanticJobIds, setSemanticJobIds] = useState<Record<"reasoning" | "shacl", string | null>>({ reasoning: null, shacl: null });
  const [sidebarWidth, setSidebarWidth] = useState(296);
  const resizingSidebar = useRef(false);
  const [searchInput, setSearchInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const summary = useProjectSummary(projectId);
  const sourceId = summary.data?.sources.find((source) => source.roles.includes("ontology"))?.id ?? summary.data?.sources[0]?.id;
  const rootHierarchy = useHierarchy(projectId, sourceId);
  const search = useProjectSearch(projectId, searchText);
  const staged = useStagedChanges(projectId);
  const activeIri = searchParams.get("iri");
  const activeTab = useMemo(() => tabs.find((tab) => tab.iri === activeIri), [tabs, activeIri]);

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

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearchText(searchInput.trim());
  }

  function openModule(moduleId: ModuleId) {
    setAccountOpen(false);
    setActiveModule(moduleId);
  }

  if (summary.isPending) return <main className="app-shell state-page"><p role="status">Loading project...</p></main>;
  if (summary.isError) return <main className="app-shell state-page"><p role="alert">Could not load project. {summary.error.message}</p></main>;
  const project = summary.data.project;
  const module = modules.find((item) => item.id === activeModule) ?? modules[0];
  const stagedCount = staged.data?.entries?.length ?? 0;

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
          <div className="rail-modules" role="tablist" aria-label="Workbench tabs" aria-orientation="vertical">
            {modules.map((item) => <button key={item.id} type="button" role="tab" className={`rail-button ${activeModule === item.id ? "rail-button-active" : ""}`} aria-label={item.label} title={item.label} aria-selected={activeModule === item.id} aria-controls="entity-workspace-panel" onClick={() => openModule(item.id)}><Icon name={item.icon} /><span className="rail-label">{item.label}</span>{item.id === "changes" && stagedCount ? <span className="rail-count">{stagedCount}</span> : null}</button>)}
          </div>
          <div className="rail-spacer" />
          <div className="rail-account">
            <button className={`rail-button account-button ${accountOpen ? "rail-button-active" : ""}`} type="button" aria-label="Account" aria-expanded={accountOpen} onClick={() => setAccountOpen((value) => !value)}><Icon name="account" /><span className="rail-label">Account</span></button>
            {accountOpen ? <div className="account-menu" role="dialog" aria-label="Account menu"><div className="account-menu-header"><span className="overline">Account</span><strong>alice</strong><span className="muted">Local reviewer</span></div><AiCredentialSettings /></div> : null}
          </div>
        </nav>
        <aside className="sidebar" aria-label="Project navigation">
          <div className="sidebar-header"><div><span className="overline">Navigate</span><h2>Ontology</h2></div><StatusBadge tone="asserted">{summary.data.symbolCount} symbols</StatusBadge></div>
          <section className="sidebar-section sidebar-search" aria-labelledby="search-heading">
            <div className="section-heading compact"><h3 id="search-heading">Find entities</h3><Icon name="search" /></div>
            <form onSubmit={submitSearch} role="search">
              <label htmlFor="entity-search">Search by label</label>
              <div className="search-row"><input id="entity-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Customer" /><button type="submit">Search</button></div>
            </form>
            {searchText ? <SearchResults query={search} onOpen={openEntity} /> : <p className="muted">Labels, definitions, and alternate labels.</p>}
          </section>
          <section className="sidebar-section" aria-labelledby="sources-heading">
            <div className="section-heading compact"><h3 id="sources-heading">Sources</h3><span className="count-label">{summary.data.sources.length}</span></div>
            <ul className="source-list">{summary.data.sources.map((source) => <li key={source.id}><strong>{source.id}</strong><small>{source.path}</small></li>)}</ul>
          </section>
          <section className="sidebar-section hierarchy-section" aria-labelledby="hierarchy-heading">
            <div className="section-heading compact"><h3 id="hierarchy-heading">Project outline</h3>{rootHierarchy.isFetching ? <span className="status-text">Refreshing</span> : null}</div>
            {rootHierarchy.isPending ? <p role="status">Loading hierarchy...</p> : null}
            {rootHierarchy.isError ? <p role="alert">Hierarchy unavailable.</p> : null}
            {rootHierarchy.data?.page.items.length ? <div className="hierarchy-viewport"><ul className="hierarchy-list">{rootHierarchy.data.page.items.map((item) => <HierarchyNode key={`${item.sourceId}:${item.iri}`} projectId={projectId} item={item} depth={0} onOpen={openEntity} />)}</ul></div> : null}
          </section>
          <div className="sidebar-footer"><span>Graph statements</span><strong>{summary.data.graphTripleCount}</strong></div>
          <button type="button" className="pane-resizer" role="separator" aria-label="Resize ontology sidebar" aria-orientation="vertical" aria-valuemin={240} aria-valuemax={420} aria-valuenow={sidebarWidth} title="Resize ontology sidebar" onPointerDown={() => { resizingSidebar.current = true; }} onKeyDown={adjustSidebar} />
        </aside>
        <section className="workspace-pane" aria-label="Entity workspace">
          <div className="workspace-toolbar"><div><span className="overline">{module.label}</span><span className="toolbar-title">{activeTab?.label ?? "Semantic workspace"}</span></div><div className="toolbar-stats"><span>{summary.data.graphTripleCount} statements</span><StatusBadge tone={stagedCount ? "staged" : "neutral"}>{stagedCount} staged</StatusBadge></div></div>
          {tabs.length ? <nav className="entity-tabs" aria-label="Open entities" role="tablist">{tabs.map((tab) => <div className="entity-tab" key={tab.iri} role="presentation"><button type="button" role="tab" aria-label={tab.label} aria-selected={tab.iri === activeIri} tabIndex={tab.iri === activeIri ? 0 : -1} onClick={() => openEntity(tab)}>{tab.label}<small>{tab.kind}</small></button><button type="button" className="tab-close" aria-label={`Close ${tab.label}`} onClick={() => closeTab(tab.iri)}><Icon name="close" /></button></div>)}</nav> : null}
          <div className="workspace-content" id="entity-workspace-panel" role="tabpanel" aria-label={activeTab ? `${activeTab.label} details` : "Entity details"} aria-live="polite">
            {renderModule(activeModule, projectId, sourceId, activeTab, stagedCount, semanticJobIds, (kind, status) => setSemanticJobIds((current) => ({ ...current, [kind]: status.id })), activeTab ? <EntityDetails projectId={projectId} iri={activeTab.iri} /> : <EmptyWorkspace />)}
          </div>
          <div className="staged-dock" aria-label="Shared staged changes"><div><span className="overline">Shared review queue</span><strong>{stagedCount ? `${stagedCount} change${stagedCount === 1 ? "" : "s"} staged` : "No staged changes"}</strong></div><span className="dock-meta">Draft → Preview → Review → Apply</span><button type="button" onClick={() => openModule("changes")}>{stagedCount ? "Review changes" : "Open changes"}</button></div>
        </section>
      </div>
    </main>
  );
}

function renderModule(module: ModuleId, projectId: string, sourceId: string | undefined, activeTab: EntityTab | undefined, stagedCount: number, semanticJobIds: Record<"reasoning" | "shacl", string | null>, onSemanticJobSubmitted: (kind: "reasoning" | "shacl", status: { id: string }) => void, exploreContent: React.ReactNode) {
  if (module === "explore") return <div className="explore-layout"><div className="entity-surface">{exploreContent}</div>{sourceId ? <aside className="inspector-drawer" aria-label="Edit inspector"><div className="drawer-heading"><div><span className="overline">Draft</span><h2>Contextual edit</h2></div><StatusBadge tone={stagedCount ? "staged" : "neutral"}>{stagedCount} staged</StatusBadge></div><StagingPanel projectId={projectId} sourceId={sourceId} /></aside> : null}</div>;
  if (module === "changes") return sourceId ? <div className="module-page"><PageIntro eyebrow="Review" title="Changes" description="A shared queue for staged edits. Nothing writes to the source until an approved proposal is applied." /><StagingPanel projectId={projectId} sourceId={sourceId} /></div> : <Unavailable />;
  if (module === "reasoning") return <div className="module-page"><PageIntro eyebrow="Semantic status" title="Reasoning" description="Deterministic reasoning against the applied graph or the current proposal." /><SemanticJobPanel projectId={projectId} initialJobId={semanticJobIds.reasoning} onJobSubmitted={(kind, status) => onSemanticJobSubmitted(kind, status)} /></div>;
  if (module === "constraints") return <div className="module-page"><PageIntro eyebrow="Constraint checks" title="SHACL validation" description="Inspect validation status without losing the distinction between asserted graph and proposal graph." /><SemanticJobPanel projectId={projectId} initialJobId={semanticJobIds.shacl} onJobSubmitted={(kind, status) => onSemanticJobSubmitted(kind, status)} /></div>;
  if (module === "fibo") return sourceId ? <div className="module-page"><PageIntro eyebrow="External ontology" title="FIBO" description="Browse the pinned, read-only catalog and stage reuse proposals through the shared review queue." /><ExternalOntologyPanel projectId={projectId} sourceId={sourceId} /></div> : <Unavailable />;
  if (module === "activity") return <div className="module-page"><PageIntro eyebrow="Collaboration" title="Activity" description="Quiet presence and activity signals keep shared work understandable." /><CollaborationActivity projectId={projectId} activeEntityIri={activeTab?.iri ?? null} /></div>;
  if (module === "assistant") return <div className="module-page"><PageIntro eyebrow="Bounded semantic help" title="AI assistant" description="Assistant responses remain advisory. Suggestions become staged edits only after explicit review." /><AiAssistantPanel projectId={projectId} entity={activeTab ?? null} /></div>;
  return <div className="module-page"><PageIntro eyebrow="Workspace" title="Settings" description="Manage local workbench preferences. Account credentials are available from the account menu in the rail." /><div className="empty-state settings-empty"><h2>Workbench settings</h2><p>Use the account menu to manage the current reviewer and optional provider credential.</p></div></div>;
}

function PageIntro({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <header className="module-intro"><span className="overline">{eyebrow}</span><h1>{title}</h1><p>{description}</p></header>;
}

function CollaborationActivity({ projectId, activeEntityIri }: { projectId: string; activeEntityIri: string | null }) {
  return <section className="activity-surface"><CollaborationPresence projectId={projectId} activeEntityIri={activeEntityIri} /><p className="muted">Activity is delivered by the collaboration channel and reflects server-authoritative project events.</p></section>;
}

function Unavailable() { return <div className="empty-state"><h2>Source unavailable</h2><p>This workspace needs an ontology source before it can open.</p></div>; }

function SearchResults({ query, onOpen }: { query: ReturnType<typeof useProjectSearch>; onOpen: (entity: WebEntityReference) => void }) {
  if (query.isPending) return <p role="status">Searching...</p>;
  if (query.isError) return <p role="alert">Search unavailable.</p>;
  if (!query.data?.page.items.length) return <p className="muted">No matching entities.</p>;
  return <ul className="search-results">{query.data.page.items.map((result) => <li key={`${result.sourceId}:${result.iri}`}><button type="button" onClick={() => onOpen({ iri: result.iri, label: result.label, kind: result.kind, sourceId: result.sourceId })}><strong>{result.label}</strong><small>{result.kind} · {result.reason}</small></button></li>)}</ul>;
}

function EmptyWorkspace() { return <div className="empty-workspace"><span className="overline">Explore</span><h1>Select an entity</h1><p>Choose a class from the outline or search by label to open its details in a tab.</p><div className="empty-hints"><span>Label-first navigation</span><span>Technical details on demand</span></div></div>; }
