import { FormEvent, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import EntityDetails from "./EntityDetails";
import HierarchyNode from "./HierarchyNode";
import StagingPanel from "./StagingPanel";
import CollaborationPresence from "./CollaborationPresence";
import { useHierarchy, useProjectSearch, useProjectSummary } from "../web/queries";
import type { WebEntityReference } from "../web/projectApi";

interface EntityTab extends WebEntityReference {
  openedAt: number;
}

export default function ProjectWorkspace() {
  const { projectId = "" } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tabs, setTabs] = useState<EntityTab[]>([]);
  const [searchInput, setSearchInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const summary = useProjectSummary(projectId);
  const sourceId = summary.data?.sources[0]?.id;
  const rootHierarchy = useHierarchy(projectId, sourceId);
  const search = useProjectSearch(projectId, searchText);
  const activeIri = searchParams.get("iri");
  const activeTab = useMemo(() => tabs.find((tab) => tab.iri === activeIri), [tabs, activeIri]);

  function openEntity(entity: WebEntityReference) {
    setTabs((existing) => existing.some((tab) => tab.iri === entity.iri) ? existing : [...existing, { ...entity, openedAt: Date.now() }]);
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

  if (summary.isPending) return <main className="app-shell"><p role="status">Loading project...</p></main>;
  if (summary.isError) return <main className="app-shell"><p role="alert">Could not load project. {summary.error.message}</p></main>;
  const project = summary.data.project;

  return (
    <main className="workbench-shell">
      <header className="global-header">
        <div>
          <p className="eyebrow">Entio</p>
          <h1>{project.name}</h1>
        </div>
        <Link className="quiet-link" to="/">Projects</Link>
      </header>
      <div className="workbench-body">
        <aside className="sidebar" aria-label="Project navigation">
          <section className="sidebar-section" aria-labelledby="search-heading">
            <h2 id="search-heading">Find entities</h2>
            <form onSubmit={submitSearch} role="search">
              <label htmlFor="entity-search">Search by label</label>
              <div className="search-row">
                <input id="entity-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Customer" />
                <button type="submit">Search</button>
              </div>
            </form>
            {searchText ? <SearchResults query={search} onOpen={openEntity} /> : <p className="muted">Search labels, alternate labels, definitions, or IRIs.</p>}
          </section>
          <section className="sidebar-section" aria-labelledby="sources-heading">
            <div className="section-heading compact"><h2 id="sources-heading">Sources</h2><span>{summary.data.sources.length}</span></div>
            <ul className="source-list">
              {summary.data.sources.map((source) => <li key={source.id}><strong>{source.id}</strong><small>{source.path}</small></li>)}
            </ul>
          </section>
          <section className="sidebar-section" aria-labelledby="hierarchy-heading">
            <div className="section-heading compact"><h2 id="hierarchy-heading">Hierarchy</h2>{rootHierarchy.isFetching ? <span className="status-text">Refreshing</span> : null}</div>
            {rootHierarchy.isPending ? <p role="status">Loading hierarchy...</p> : null}
            {rootHierarchy.isError ? <p role="alert">Hierarchy unavailable.</p> : null}
            {rootHierarchy.data?.page.items.length ? <ul className="hierarchy-list">{rootHierarchy.data.page.items.map((item) => <HierarchyNode key={`${item.sourceId}:${item.iri}`} projectId={projectId} item={item} depth={0} onOpen={openEntity} />)}</ul> : null}
          </section>
        </aside>
        <section className="workspace-pane" aria-label="Entity workspace">
          <div className="workspace-toolbar">
            <span>{summary.data.symbolCount} symbols</span>
            <span>{summary.data.graphTripleCount} graph statements</span>
            <CollaborationPresence projectId={projectId} activeEntityIri={activeIri} />
          </div>
          {tabs.length ? <nav className="entity-tabs" aria-label="Open entities">{tabs.map((tab) => <div className={`entity-tab ${tab.iri === activeIri ? "active" : ""}`} key={tab.iri}><button type="button" onClick={() => openEntity(tab)}>{tab.label}</button><button type="button" className="tab-close" aria-label={`Close ${tab.label}`} onClick={() => closeTab(tab.iri)}>×</button></div>)}</nav> : null}
          {activeTab ? <EntityDetails projectId={projectId} iri={activeTab.iri} /> : <EmptyWorkspace />}
          {sourceId ? <StagingPanel projectId={projectId} sourceId={sourceId} /> : null}
        </section>
      </div>
    </main>
  );
}

function SearchResults({ query, onOpen }: { query: ReturnType<typeof useProjectSearch>; onOpen: (entity: WebEntityReference) => void }) {
  if (query.isPending) return <p role="status">Searching...</p>;
  if (query.isError) return <p role="alert">Search unavailable.</p>;
  if (!query.data?.page.items.length) return <p className="muted">No matching entities.</p>;
  return <ul className="search-results">{query.data.page.items.map((result) => <li key={`${result.sourceId}:${result.iri}`}><button type="button" onClick={() => onOpen({ iri: result.iri, label: result.label, kind: result.kind, sourceId: result.sourceId })}><strong>{result.label}</strong><small>{result.kind} · {result.reason}</small></button></li>)}</ul>;
}

function EmptyWorkspace() {
  return <div className="empty-workspace"><p className="eyebrow">Workspace</p><h2>Select an entity</h2><p>Choose a class from the hierarchy or search by label to open its details in a tab.</p></div>;
}
