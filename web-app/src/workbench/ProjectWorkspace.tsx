import { type DragEvent, type KeyboardEvent, type MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import EntityDetails, { type EntitySectionTarget } from "./EntityDetails";
import HierarchyNode from "./HierarchyNode";
import StagingPanel from "./StagingPanel";
import CollaborationPresence from "./CollaborationPresence";
import SemanticJobPanel from "./SemanticJobPanel";
import ExternalOntologyPanel from "./ExternalOntologyPanel";
import AiCredentialSettings from "./AiCredentialSettings";
import AiProposalPanel from "./AiProposalPanel";
import ProfileSettings from "./ProfileSettings";
import Icon from "../components/ui/Icon";
import StatusBadge from "../components/ui/StatusBadge";
import { useHierarchy, useProjectActivity, useProjectOutline, useProjectSearch, useProjectSummary, useSemanticJobDetails, useShaclShapes, useStagedChanges } from "../web/queries";
import type { WebEntityDetailResponse, WebEntityReference, WebHierarchyItem, WebOutlineItem, WebShaclConstraintSummary, WebShaclShapeSummary, WebStagedEntry } from "../web/projectApi";
import { entityKindPresentation } from "./entityKindPresentation";
import {
  ContextMenu,
  ContextualEditDialog,
  type ContextMenuState,
  type ContextualEditor,
} from "./ContextualEditing";
import type { WebStagingEditType } from "./stagingEditTypes";
import OntologyMapShell, { type OntologyMapViewState } from "./ontology-map/OntologyMapShell";
import type { OntologyGraphEdgeKind, OntologyGraphNodeKind } from "../web/contracts";

type ModuleId = "explore" | "changes" | "reasoning" | "validation" | "fibo" | "activity" | "settings";
type RailItemId = ModuleId;
type OutlineTabId = "classes" | "objects" | "properties";

interface EntityTab extends WebEntityReference {
  openedAt: number;
  directType?: WebEntityReference | null;
  requestedSection?: EntitySectionTarget;
  sectionRequestId?: number;
}

interface OpenEditor {
  sourceId: string;
  editor: ContextualEditor;
}

interface StagedEntityReference extends WebEntityReference {
  kind: string;
  sourceId: string;
  directType?: WebEntityReference | null;
}

const modules: Array<{ id: ModuleId; label: string; icon: Parameters<typeof Icon>[0]["name"] }> = [
  { id: "explore", label: "Explore", icon: "explore" },
  { id: "changes", label: "Proposal", icon: "changes" },
  { id: "reasoning", label: "Reasoning", icon: "reasoning" },
  { id: "validation", label: "Validation", icon: "constraints" },
  { id: "fibo", label: "FIBO", icon: "fibo" },
  { id: "activity", label: "Activity", icon: "activity" },
  { id: "settings", label: "Settings", icon: "settings" },
];

const railItems: Array<{ id: RailItemId; label: string; icon: Parameters<typeof Icon>[0]["name"] }> = modules;

const DISPLAY_NAME_STORAGE_KEY = "entio.displayName";
const ASSISTANT_OPEN_STORAGE_KEY = "entio.assistantOpen";
const DEFAULT_DISPLAY_NAME = "Alice Contributor";
const DEFAULT_MAP_NODE_KINDS: OntologyGraphNodeKind[] = ["Class", "ObjectProperty", "DatatypeProperty", "Individual"];
const DEFAULT_MAP_EDGE_KINDS: OntologyGraphEdgeKind[] = ["SubclassOf", "Domain", "Range", "Type", "ObjectAssertion"];
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

function loadAssistantOpen(): boolean {
  try {
    return window.localStorage?.getItem(ASSISTANT_OPEN_STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

function persistAssistantOpen(open: boolean) {
  try {
    window.localStorage?.setItem(ASSISTANT_OPEN_STORAGE_KEY, String(open));
  } catch {
    // The current in-memory state still works when persistent browser storage is unavailable.
  }
}

export default function ProjectWorkspace({ initialModule = "explore" }: { initialModule?: ModuleId }) {
  const { projectId = "" } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tabs, setTabs] = useState<EntityTab[]>([]);
  const [mapOpen, setMapOpen] = useState(() => searchParams.get("view") === "map");
  const [mapSeed, setMapSeed] = useState<WebEntityReference | undefined>();
  const [mapViewState, setMapViewState] = useState<OntologyMapViewState>({ selectedNodeId: null, nodeKinds: DEFAULT_MAP_NODE_KINDS, edgeKinds: DEFAULT_MAP_EDGE_KINDS });
  const outlineFilterMenuRef = useRef<HTMLDetailsElement>(null);
  const outlineClickTimer = useRef<number | null>(null);
  const [mapLoadedEntities, setMapLoadedEntities] = useState<Record<string, string>>({});
  const [activeModule, setActiveModule] = useState<ModuleId>(initialModule);
  const [railExpanded, setRailExpanded] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [assistantOpen, setAssistantOpen] = useState(loadAssistantOpen);
  const [displayName, setDisplayName] = useState(loadDisplayName);
  const [semanticJobIds, setSemanticJobIds] = useState<Record<"reasoning" | "shacl", string | null>>({ reasoning: null, shacl: null });
  const [sidebarWidth, setSidebarWidth] = useState(296);
  const resizingSidebar = useRef(false);
  const [searchInput, setSearchInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const [outlineTab, setOutlineTab] = useState<OutlineTabId>("classes");
  const [expandedClassIris, setExpandedClassIris] = useState<Set<string>>(() => new Set());
  const [collapsedObjectGroupIris, setCollapsedObjectGroupIris] = useState<Set<string>>(() => new Set());
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
  const mapActive = searchParams.get("view") === "map";
  const activeTab = useMemo(() => tabs.find((tab) => tab.iri === activeIri), [tabs, activeIri]);
  const stagedEntries = staged.data?.entries ?? [];
  const proposalStatus = staged.data?.proposal?.status;
  const stagedIsPendingReview = proposalStatus !== "APPROVED" && proposalStatus !== "APPLIED";
  const stagedLabels = useMemo(() => stagedEntityLabelOverrides(stagedEntries), [stagedEntries]);
  const stagedIris = useMemo(() => stagedEntityIris(stagedEntries), [stagedEntries]);
  const stagedCreated = useMemo(() => proposalStatus === "APPLIED" ? [] : stagedCreatedEntities(stagedEntries), [proposalStatus, stagedEntries]);
  const stagedClassHierarchy = useMemo(() => proposalStatus === "APPLIED" ? emptyStagedClassHierarchy() : buildStagedClassHierarchy(stagedEntries), [proposalStatus, stagedEntries]);
  const stagedDetails = useMemo(() => proposalStatus === "APPLIED" ? new Map<string, WebEntityDetailResponse>() : buildStagedEntityDetails(stagedEntries), [proposalStatus, stagedEntries]);

  useEffect(() => {
    setActiveModule(initialModule);
    setMapOpen(searchParams.get("view") === "map");
    setMapSeed(undefined);
    setMapViewState({ selectedNodeId: null, nodeKinds: DEFAULT_MAP_NODE_KINDS, edgeKinds: DEFAULT_MAP_EDGE_KINDS, sourceVisible: true });
    setMapLoadedEntities({});
  }, [initialModule, projectId]);

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
    if (!stagedIsPendingReview || stagedLabels.size === 0) return;
    setTabs((current) => current.map((tab) => {
      const stagedLabel = stagedLabels.get(tab.iri);
      return stagedLabel && stagedLabel !== tab.label ? { ...tab, label: stagedLabel } : tab;
    }));
  }, [stagedIsPendingReview, stagedLabels]);

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

  function openEntity(entity: WebEntityReference, section?: EntitySectionTarget) {
    const supportedMapKind = ["class", "objectproperty", "datatypeproperty", "individual"].includes(entity.kind?.toLowerCase() ?? "");
    if (mapActive && entity.sourceId === sourceId && supportedMapKind) {
      const loadedId = mapLoadedEntities[`${entity.sourceId}\u0000${entity.iri}`];
      if (loadedId) {
        setMapViewState((current) => ({ ...current, selectedNodeId: loadedId }));
        return;
      }
      if ((mapViewState.expanded || Boolean(mapViewState.positions)) && !window.confirm("Replace the temporary map with a view centered on this entity?")) return;
      setMapSeed(entity);
      setMapViewState((current) => ({ ...current, selectedNodeId: null }));
      return;
    }
    openEntityDetails(entity, section);
  }

  function openEntityDetails(entity: WebEntityReference, section?: EntitySectionTarget) {
    setTabs((existing) => {
      const current = existing.find((tab) => tab.iri === entity.iri);
      if (!current) return [...existing, { ...entity, openedAt: Date.now(), requestedSection: section, sectionRequestId: section ? Date.now() : undefined }];
      return existing.map((tab) => tab.iri === entity.iri ? {
        ...tab,
        ...entity,
        requestedSection: section ?? tab.requestedSection,
        sectionRequestId: section ? Date.now() : tab.sectionRequestId,
      } : tab);
    });
    setActiveModule("explore");
    navigate(`/projects/${encodeURIComponent(projectId)}?iri=${encodeURIComponent(entity.iri)}`);
  }

  function openMap() {
    setMapOpen(true);
    setMapSeed(undefined);
    setActiveModule("explore");
    const params = new URLSearchParams();
    params.set("view", "map");
    navigate(`/projects/${encodeURIComponent(projectId)}?${params}`);
  }

  function closeMap() {
    setMapOpen(false);
    setMapSeed(undefined);
    setMapViewState((current) => ({ selectedNodeId: null, nodeKinds: current.nodeKinds, edgeKinds: current.edgeKinds, sourceVisible: current.sourceVisible }));
    setMapLoadedEntities({});
    navigate(activeTab ? `/projects/${encodeURIComponent(projectId)}?iri=${encodeURIComponent(activeTab.iri)}` : `/projects/${encodeURIComponent(projectId)}`);
  }

  function setClassExpanded(iri: string, expanded: boolean) {
    setExpandedClassIris((current) => {
      const next = new Set(current);
      if (expanded) next.add(iri);
      else next.delete(iri);
      return next;
    });
  }

  function setObjectGroupExpanded(iri: string, expanded: boolean) {
    setCollapsedObjectGroupIris((current) => {
      const next = new Set(current);
      if (expanded) next.delete(iri);
      else next.add(iri);
      return next;
    });
  }

  function closeTab(iri: string) {
    const remaining = tabs.filter((tab) => tab.iri !== iri);
    setTabs(remaining);
    if (iri === activeIri) {
      const next = remaining.at(-1);
      navigate(next
        ? `/projects/${encodeURIComponent(projectId)}?iri=${encodeURIComponent(next.iri)}`
        : mapOpen
          ? `/projects/${encodeURIComponent(projectId)}?view=map`
          : `/projects/${encodeURIComponent(projectId)}`);
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

  function setAssistantVisibility(open: boolean) {
    persistAssistantOpen(open);
    setAssistantOpen(open);
  }

  function launchEditor(editor: ContextualEditor, targetSourceId = sourceId) {
    if (!targetSourceId) return;
    setContextMenu(null);
    setOpenEditor({ sourceId: targetSourceId, editor });
  }

  function toggleSharedNodeKind(kind: OntologyGraphNodeKind) {
    const current = mapViewState.nodeKinds ?? DEFAULT_MAP_NODE_KINDS;
    const enabled = !current.includes(kind);
    const nodeKinds = enabled ? [...current, kind] : current.filter((item) => item !== kind);
    setMapViewState({
      ...mapViewState,
      nodeKinds,
      revealedIndividualIds: kind === "Individual"
        ? enabled ? (mapViewState.nodes ?? []).filter((node) => node.kind === "Individual").map((node) => node.identity.id) : []
        : mapViewState.revealedIndividualIds,
    });
  }

  function toggleSharedEdgeKind(kind: OntologyGraphEdgeKind) {
    const current = mapViewState.edgeKinds ?? DEFAULT_MAP_EDGE_KINDS;
    setMapViewState({ ...mapViewState, edgeKinds: current.includes(kind) ? current.filter((item) => item !== kind) : [...current, kind] });
  }

  useEffect(() => {
    const closeFiltersOutside = (event: globalThis.PointerEvent) => {
      const menu = outlineFilterMenuRef.current;
      if (menu?.open && !menu.contains(event.target as Node)) menu.open = false;
    };
    document.addEventListener("pointerdown", closeFiltersOutside);
    return () => document.removeEventListener("pointerdown", closeFiltersOutside);
  }, []);

  useEffect(() => () => {
    if (outlineClickTimer.current !== null) window.clearTimeout(outlineClickTimer.current);
  }, []);

  function openOutlineEntity(entity: WebEntityReference) {
    if (!mapActive) {
      openEntity(entity);
      return;
    }
    if (outlineClickTimer.current !== null) window.clearTimeout(outlineClickTimer.current);
    outlineClickTimer.current = window.setTimeout(() => {
      outlineClickTimer.current = null;
      openEntity(entity);
    }, 220);
  }

  function openOutlineEntityDetails(entity: WebEntityReference) {
    if (outlineClickTimer.current !== null) {
      window.clearTimeout(outlineClickTimer.current);
      outlineClickTimer.current = null;
    }
    openEntityDetails(entity);
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
  const visibleNodeKinds = new Set(mapViewState.nodeKinds ?? DEFAULT_MAP_NODE_KINDS);
  const sourceVisible = mapViewState.sourceVisible !== false;
  const outlineItems = outline.data?.page.items ?? [];
  const createdClasses = stagedCreated.filter((item) => item.kind === "Class");
  const createdObjects = stagedCreated.filter((item) => item.kind === "Individual");
  const createdProperties = stagedCreated.filter((item) => item.kind?.endsWith("Property"));
  const classCount = sourceVisible && visibleNodeKinds.has("Class") ? outlineItems.filter((item) => item.kind === "Class").length + createdClasses.length : 0;
  const objects = outlineItems.filter((item) => item.kind === "Individual");
  const properties = outlineItems.filter((item) => item.kind.endsWith("Property"));
  const objectItems = sourceVisible && visibleNodeKinds.has("Individual") ? mergeOutlineEntities(applyStagedLabels(objects, stagedLabels), createdObjects) : [];
  const propertyItems = sourceVisible ? mergeOutlineEntities(applyStagedLabels(properties, stagedLabels), createdProperties).filter((item) => visibleNodeKinds.has(item.kind as OntologyGraphNodeKind)) : [];
  const rootClassItems = (sourceVisible && visibleNodeKinds.has("Class") ? [
    ...applyStagedLabels(rootHierarchy.data?.page.items ?? [], stagedLabels).filter((item) => !stagedClassHierarchy.childIris.has(item.iri)),
    ...stagedClassHierarchy.rootItems.filter((stagedItem) => !(rootHierarchy.data?.page.items ?? []).some((item) => item.iri === stagedItem.iri)),
  ] : []).sort((left, right) => left.label.localeCompare(right.label) || left.iri.localeCompare(right.iri));

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
              const selected = activeModule === item.id;
              return <button key={item.id} type="button" role="tab" className={`rail-button ${selected ? "rail-button-active" : ""}`} aria-label={item.label} title={item.label} aria-selected={selected} aria-controls="entity-workspace-panel" onClick={() => openModule(item.id)}><Icon name={item.icon} /><span className="rail-label">{item.label}</span>{item.id === "changes" && stagedCount ? <span className={`rail-count ${stagedIsPendingReview ? "staged-pending" : ""}`}>{stagedCount}</span> : null}</button>;
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
              <div className="outline-search-controls"><div className="search-row"><Icon name="search" /><input id="entity-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Search entities" /></div><details ref={outlineFilterMenuRef} className="outline-filter-menu"><summary className="icon-button" aria-label="Filter project outline and map"><Icon name="filter" /></summary><div className="outline-filter-popover"><fieldset><legend>Entity kinds</legend>{DEFAULT_MAP_NODE_KINDS.map((kind) => <label key={kind}><input type="checkbox" checked={visibleNodeKinds.has(kind)} onChange={() => toggleSharedNodeKind(kind)} />{kind === "ObjectProperty" ? "Object properties" : kind === "DatatypeProperty" ? "Datatype properties" : kind === "Individual" ? "Individuals" : "Classes"}</label>)}</fieldset><fieldset><legend>Map relationships</legend>{DEFAULT_MAP_EDGE_KINDS.map((kind) => <label key={kind}><input type="checkbox" checked={(mapViewState.edgeKinds ?? DEFAULT_MAP_EDGE_KINDS).includes(kind)} onChange={() => toggleSharedEdgeKind(kind)} />{kind}</label>)}</fieldset><label><input type="checkbox" checked={sourceVisible} onChange={() => setMapViewState({ ...mapViewState, sourceVisible: !sourceVisible })} />Ontology source</label><button className="button small" type="button" onClick={() => setMapViewState({ ...mapViewState, nodeKinds: DEFAULT_MAP_NODE_KINDS, edgeKinds: DEFAULT_MAP_EDGE_KINDS, sourceVisible: true, revealedIndividualIds: (mapViewState.nodes ?? []).filter((node) => node.kind === "Individual").map((node) => node.identity.id) })}>Reset filters</button></div></details></div>
            </div>
          </section>
          {searchInput.trim() ? <section className="sidebar-section sidebar-search-results" aria-labelledby="search-results-heading">
            <div className="section-heading compact"><h3 id="search-results-heading">Search results</h3><button className="icon-button" type="button" aria-label="Clear search" onClick={() => { setSearchInput(""); setSearchText(""); }}><Icon name="close" /></button></div>
            <SearchResults query={search} onOpen={openEntity} allowedKinds={visibleNodeKinds} sourceVisible={sourceVisible} />
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
                  {rootClassItems.length ? <ul className="hierarchy-list">{rootClassItems.map((item) => <HierarchyNode key={`${item.sourceId}:${item.iri}`} projectId={projectId} item={item} depth={0} onOpen={openOutlineEntity} onOpenDetails={openOutlineEntityDetails} onContextMenu={openEntityContextMenu} stagedIris={stagedIsPendingReview ? stagedIris : undefined} stagedChildrenByParent={stagedIsPendingReview ? stagedClassHierarchy.childrenByParent : undefined} expandedIris={expandedClassIris} onExpandedChange={setClassExpanded} />)}</ul> : null}
                </> : null}
                {outlineTab === "objects" ? <GroupedObjectOutline items={objectItems} loading={outline.isPending} error={outline.isError} onOpen={openOutlineEntity} onOpenDetails={openOutlineEntityDetails} onContextMenu={openEntityContextMenu} stagedIris={stagedIsPendingReview ? stagedIris : undefined} collapsedGroupIris={collapsedObjectGroupIris} onExpandedChange={setObjectGroupExpanded} /> : null}
                {outlineTab === "properties" ? <OutlineEntityList title="Properties" marker="P" items={propertyItems} loading={outline.isPending} error={outline.isError} onOpen={openOutlineEntity} onOpenDetails={openOutlineEntityDetails} onContextMenu={openEntityContextMenu} stagedIris={stagedIsPendingReview ? stagedIris : undefined} /> : null}
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
        <section className={`workspace-pane ${assistantOpen ? "workspace-pane-with-ai" : ""}`} aria-label="Entity workspace">
          <div className="workspace-main">
            <div className="workspace-toolbar"><div><span className="overline">{module.label}</span><span className="toolbar-title">{activeModule === "explore" ? mapActive ? "Ontology map" : activeTab?.label ?? "Semantic workspace" : module.label}</span></div><div className="toolbar-stats">{activeModule === "explore" ? <button className="button primary small" type="button" onClick={openMap}>View Map</button> : null}<span>{summary.data.graphTripleCount} statements</span><StatusBadge tone={stagedCount && stagedIsPendingReview ? "staged" : "neutral"}>{stagedCount} staged</StatusBadge><button className={`assistant-toggle ${assistantOpen ? "assistant-toggle-active" : ""}`} type="button" aria-label={assistantOpen ? "Hide Entio AI sidebar" : "Open Entio AI"} aria-expanded={assistantOpen} onClick={() => setAssistantVisibility(!assistantOpen)}><Icon name="assistant" /><span>AI</span></button></div></div>
            {activeModule === "explore" && (tabs.length || mapOpen) ? <>
              <nav className="entity-tabs" aria-label="Open entities" role="tablist">{mapOpen ? <div className="entity-tab" role="presentation"><button type="button" role="tab" aria-label="Ontology map" aria-selected={mapActive} tabIndex={mapActive ? 0 : -1} onClick={openMap}>Ontology map<small>Map</small></button><button type="button" className="tab-close" aria-label="Close Ontology map" onClick={closeMap}><Icon name="close" /></button></div> : null}{tabs.map((tab) => <div
                className={`entity-tab ${stagedIsPendingReview && stagedIris.has(tab.iri) ? "entity-staged" : ""} ${draggedTabIri === tab.iri ? "entity-tab-dragging" : ""}`}
                key={tab.iri}
                role="presentation"
                draggable
                onDragStart={(event) => startTabDrag(event, tab)}
                onDragEnd={() => setDraggedTabIri(null)}
                onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
                onDrop={(event) => { event.preventDefault(); const sourceIri = draggedTabIri ?? event.dataTransfer.getData("text/plain"); moveTab(sourceIri, tab.iri); setDraggedTabIri(null); }}
              ><button type="button" role="tab" aria-label={tab.label} aria-selected={tab.iri === activeIri} aria-keyshortcuts="Alt+ArrowLeft Alt+ArrowRight" tabIndex={tab.iri === activeIri ? 0 : -1} onClick={() => openEntityDetails(tab)} onKeyDown={(event) => moveTabByKeyboard(event, tab)}>{tab.label}<small>{tab.kind}</small></button><button type="button" className="tab-close" aria-label={`Close ${tab.label}`} onClick={() => closeTab(tab.iri)}><Icon name="close" /></button></div>)}</nav>
              <span className="visually-hidden" role="status" aria-live="polite">{tabOrderMessage}</span>
            </> : null}
            <div className={`workspace-content ${activeModule === "explore" && activeIri && stagedIsPendingReview && stagedIris.has(activeIri) ? "workspace-content-staged" : ""}`} id="entity-workspace-panel" role="tabpanel" aria-label={activeModule === "explore" ? activeTab ? `${activeTab.label} details` : "Entity details" : `${module.label} workspace`} aria-live="polite">
              {renderModule(activeModule, projectId, sourceId, shapesSourceId, activeTab, semanticJobIds, (kind, status) => setSemanticJobIds((current) => ({ ...current, [kind]: status.id })), mapActive && mapOpen && sourceId ? <OntologyMapShell projectId={projectId} sourceId={sourceId} seed={mapSeed} state={mapViewState} onStateChange={setMapViewState} onViewDetails={openEntityDetails} onLoadedEntities={setMapLoadedEntities} /> : activeTab ? <EntityDetails projectId={projectId} iri={activeTab.iri} stagedEntity={stagedDetails.get(activeTab.iri)} stagedEntries={stagedIsPendingReview ? stagedEntries : []} directType={activeTab.directType} initialSection={activeTab.requestedSection} sectionRequestId={activeTab.sectionRequestId} onOpenEntity={openEntity} /> : <EmptyWorkspace onOpenMap={openMap} />, displayName, saveDisplayName, launchEditor)}
            </div>
            {activeModule !== "changes" ? <div className={`staged-dock ${stagedCount && stagedIsPendingReview ? "staged-dock-pending" : ""}`} aria-label="Shared staged changes"><div><span className="overline">Shared review queue</span><strong>{stagedCount ? `${stagedCount} change${stagedCount === 1 ? "" : "s"} staged` : "No staged changes"}</strong></div><span className="dock-meta">Review the complete proposal, then accept or reject it.</span><button type="button" onClick={() => openModule("changes")}>{stagedCount ? "Review proposal" : "Open proposal"}</button></div> : null}
          </div>
          <aside className={`ai-sidebar ${assistantOpen ? "" : "ai-sidebar-closed"}`} aria-label="Entio AI assistant" aria-hidden={!assistantOpen}><div className="ai-sidebar-heading"><div><span className="overline">Entio AI</span><strong>Assistant</strong></div><button className="icon-button" type="button" aria-label="Close Entio AI" onClick={() => setAssistantVisibility(false)}><Icon name="close" /></button></div><AiProposalPanel projectId={projectId} onStaged={() => void staged.refetch()} stagedAiRunIds={stagedEntries.map((entry) => entry.normalizedValues.aiRunId).filter((runId): runId is string => Boolean(runId))} compact /></aside>
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
  if (module === "reasoning") return <ReasoningWorkspace projectId={projectId} initialJobId={semanticJobIds.reasoning} onJobSubmitted={onSemanticJobSubmitted} />;
  if (module === "validation") return <ValidationWorkspace projectId={projectId} shapesSourceId={shapesSourceId} shaclJobId={semanticJobIds.shacl} onJobSubmitted={onSemanticJobSubmitted} onOpen={(editType) => onOpenEditor({ kind: "typed", editType }, shapesSourceId)} />;
  if (module === "fibo") return sourceId ? <div className="module-page"><PageIntro eyebrow="External ontology" title="FIBO" description="Browse the pinned, read-only catalog and stage reuse proposals through the shared review queue." /><ExternalOntologyPanel projectId={projectId} sourceId={sourceId} /></div> : <Unavailable />;
  if (module === "activity") return <div className="module-page"><PageIntro eyebrow="Collaboration" title="Activity" description="Quiet presence and activity signals keep shared work understandable." /><CollaborationActivity projectId={projectId} activeEntityIri={activeTab?.iri ?? null} /></div>;
  return <div className="module-page"><PageIntro eyebrow="Workspace" title="Settings" description="Manage your local profile and optional provider credentials." /><div className="settings-grid"><ProfileSettings displayName={displayName} onSave={onDisplayNameSave} /><AiCredentialSettings /></div></div>;
}

function PageIntro({ eyebrow: _eyebrow, title: _title, description }: { eyebrow: string; title: string; description: string }) {
  return <header className="module-intro"><p>{description}</p></header>;
}

function CollaborationActivity({ projectId, activeEntityIri }: { projectId: string; activeEntityIri: string | null }) {
  const activity = useProjectActivity(projectId);
  return <section className="activity-surface"><CollaborationPresence projectId={projectId} activeEntityIri={activeEntityIri} /><div className="activity-history"><div className="section-heading compact"><div><span className="overline">Project history</span><h2>Shared updates</h2></div><span>{activity.data?.events.length ?? 0}</span></div>{activity.isPending ? <p role="status">Loading activity...</p> : null}{activity.isError ? <p role="alert">Could not load activity history.</p> : null}{!activity.isPending && !activity.isError && !activity.data?.events.length ? <p className="muted">No shared updates yet.</p> : null}<ol aria-label="Project activity history">{activity.data?.events.map((event) => <li key={event.eventId}><span className="activity-event-marker" aria-hidden="true" /> <div><strong>{describeActivityEvent(event.eventType)}</strong><span>{event.userId ?? "Entio"} · {formatActivityTime(event.timestamp)}</span></div></li>)}</ol>{activity.data?.truncated ? <p className="muted">Older updates are not currently loaded.</p> : null}</div></section>;
}

function describeActivityEvent(eventType: string): string {
  if (eventType === "staged-change.updated") return "Staged changes updated";
  if (eventType === "proposal.previewed") return "Proposal previewed";
  if (eventType === "proposal.approved") return "Proposal approved";
  if (eventType === "proposal.rejected") return "Proposal rejected";
  if (eventType === "proposal.applied") return "Proposal applied";
  if (eventType === "proposal.conflicted") return "Proposal application conflicted";
  if (eventType.startsWith("semantic-job.")) return "Semantic job updated";
  return eventType;
}

function formatActivityTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString([], { dateStyle: "short", timeStyle: "short" });
}

interface ReasoningWorkspaceProps {
  projectId: string;
  initialJobId: string | null;
  onJobSubmitted: (kind: "reasoning" | "shacl", status: { id: string }) => void;
}

function ReasoningWorkspace({ projectId, initialJobId, onJobSubmitted }: ReasoningWorkspaceProps) {
  return <div className="module-page reasoning-page">
    <PageIntro eyebrow="Semantic status" title="Reasoning" description="Deterministic reasoning against the applied graph or the current proposal." />
    <section className="reasoning-section" aria-label="Reasoning">
      <SemanticJobPanel projectId={projectId} initialJobId={initialJobId} headingId="reasoning-job-heading" showShacl={false} onJobSubmitted={onJobSubmitted} />
    </section>
  </div>;
}

interface ValidationWorkspaceProps {
  projectId: string;
  shapesSourceId?: string;
  shaclJobId: string | null;
  onJobSubmitted: (kind: "reasoning" | "shacl", status: { id: string }) => void;
  onOpen: (editType: WebStagingEditType) => void;
}

function ValidationWorkspace({ projectId, shapesSourceId, shaclJobId, onJobSubmitted, onOpen }: ValidationWorkspaceProps) {
  const shapes = useShaclShapes(projectId);
  const shaclDetails = useSemanticJobDetails(projectId, shaclJobId);
  const [actionDialog, setActionDialog] = useState<"add" | "manage" | null>(null);
  const [viewConstraints, setViewConstraints] = useState(false);
  const [viewValidationResults, setViewValidationResults] = useState(false);
  const findings = shaclDetails.data?.shaclFindings ?? [];
  const validationComplete = shaclDetails.data?.job.status === "Completed";

  return <div className="module-page validation-page">
    <PageIntro eyebrow="Validation" title="Validation" description="Validate the applied graph or current proposal against its SHACL constraints." />
    <section className="constraints-toolbar" aria-labelledby="constraint-authoring-heading">
      <div><span className="overline">Shape authoring</span><h2 id="constraint-authoring-heading">Manage constraints</h2><p>Every edit enters the shared review queue. Source files change only after proposal approval.</p></div>
      {shapesSourceId ? <div className="button-row">
        <button className="button primary" type="button" onClick={() => setActionDialog("add")}>Add constraint</button>
        <button className="button" type="button" onClick={() => setActionDialog("manage")}>Manage constraints</button>
      </div> : <p role="note" className="workflow-warning">No writable SHACL shapes source is configured.</p>}
    </section>

    <section className="constraints-validation" aria-labelledby="constraints-validation-heading">
      <div className="section-heading compact"><div><span className="overline">Validation</span><h2 id="constraints-validation-heading">Validate SHACL</h2><p className="muted">Run validation to check every constraint below.</p></div></div>
      <SemanticJobPanel projectId={projectId} initialJobId={shaclJobId} headingId="shacl-job-heading" showReasoning={false} showHeading={false} autoStartReasoning={false} onJobSubmitted={onJobSubmitted} />
      {validationComplete ? <div className="validation-result-action"><button className="button primary" type="button" onClick={() => setViewValidationResults(true)}>View Results</button></div> : null}
    </section>
    {actionDialog ? <ConstraintActionDialog mode={actionDialog} onClose={() => setActionDialog(null)} onView={() => { setActionDialog(null); setViewConstraints(true); }} onOpen={(editType) => { setActionDialog(null); onOpen(editType); }} /> : null}
    {viewConstraints ? <ConstraintViewDialog shapes={shapes.data?.shapes ?? []} loading={shapes.isPending} error={shapes.isError ? shapes.error.message : null} onClose={() => setViewConstraints(false)} /> : null}
    {viewValidationResults ? <ConstraintViewDialog title="Validation results" description="SHACL validation results for the selected graph." shapes={shapes.data?.shapes ?? []} findings={findings} checked loading={shapes.isPending} error={shapes.isError ? shapes.error.message : null} onClose={() => setViewValidationResults(false)} /> : null}
  </div>;
}

function ConstraintActionDialog({ mode, onClose, onView, onOpen }: { mode: "add" | "manage"; onClose: () => void; onView: () => void; onOpen: (editType: WebStagingEditType) => void }) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  const title = mode === "add" ? "Add constraint" : "Manage constraints";
  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="edit-dialog constraint-action-dialog" role="dialog" aria-modal="true" aria-labelledby="constraint-action-heading">
      <header className="edit-dialog-header"><div><span className="overline">SHACL</span><h2 id="constraint-action-heading">{title}</h2></div><button className="icon-button" type="button" aria-label="Close constraint actions" onClick={onClose}>×</button></header>
      {confirmDelete ? <div className="constraint-delete-confirm"><h3>Delete a shape?</h3><p>This removes a SHACL shape through the review workflow. The source file will not change until the proposal is approved.</p><div className="button-row"><button className="button" type="button" onClick={() => setConfirmDelete(false)}>Cancel</button><button className="button danger" type="button" onClick={() => onOpen("shacl-delete-shape")}>Continue to delete</button></div></div> : <div className="constraint-action-options">
        {mode === "add" ? <>
          <button className="constraint-action-option" type="button" onClick={() => onOpen("shacl-create-node-shape")}><strong>Create node shape</strong><span>Define a shape that targets a class or node.</span></button>
          <button className="constraint-action-option" type="button" onClick={() => onOpen("shacl-create-property-shape")}><strong>Add property constraint</strong><span>Constrain a property on an existing shape.</span></button>
        </> : <>
          <button className="constraint-action-option" type="button" onClick={onView}><strong>View constraints</strong><span>Review the current shapes and property rules.</span></button>
          <button className="constraint-action-option" type="button" onClick={() => onOpen("shacl-update-constraint")}><strong>Update constraint</strong><span>Change an existing rule or validation message.</span></button>
          <button className="constraint-action-option" type="button" onClick={() => onOpen("shacl-remove-constraint")}><strong>Remove constraint</strong><span>Remove one property rule from a shape.</span></button>
          <button className="constraint-action-option constraint-action-danger" type="button" onClick={() => setConfirmDelete(true)}><strong>Delete shape</strong><span>Remove an entire SHACL shape.</span></button>
        </>}
      </div>}
    </section>
  </div>;
}

function ConstraintViewDialog({ title = "View constraints", description = "Current node shapes and property constraints in the project.", shapes, findings = [], checked = false, loading, error, onClose }: { title?: string; description?: string; shapes: WebShaclShapeSummary[]; findings?: Array<{ shapeIri: string; constraint: string }>; checked?: boolean; loading: boolean; error: string | null; onClose: () => void }) {
  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="edit-dialog constraint-view-dialog" role="dialog" aria-modal="true" aria-labelledby="constraint-view-heading">
      <header className="edit-dialog-header"><div><span className="overline">SHACL</span><h2 id="constraint-view-heading">{title}</h2></div><button className="icon-button" type="button" aria-label={title === "View constraints" ? "Close constraint view" : "Close validation results"} onClick={onClose}>×</button></header>
      <p className="edit-dialog-description">{description}</p>
      {loading ? <p role="status">Loading constraints...</p> : null}
      {error ? <p role="alert" className="workflow-error">Could not load constraints. {error}</p> : null}
      {!loading && !error && !shapes.length ? <div className="constraints-empty"><strong>No SHACL shapes</strong><span>No constraints are configured for this project.</span></div> : null}
      {!loading && !error ? <div className="constraints-shape-list">{shapes.map((shape) => <ConstraintShapeSummary key={shape.iri} shape={shape} findings={findings} checked={checked} />)}</div> : null}
    </section>
  </div>;
}

function ConstraintShapeSummary({ shape, findings, checked }: { shape: WebShaclShapeSummary; findings: Array<{ shapeIri: string; constraint: string }>; checked: boolean }) {
  return <article className="constraint-shape-summary">
    <header><div><strong>{shape.label}</strong><span>{shape.targets.map((target) => `${humanizeShaclTarget(target.kind)}: ${target.label}`).join(" · ") || "No target"}</span></div><StatusBadge tone={shape.severity === "Violation" ? "danger" : "neutral"}>{shape.severity}</StatusBadge></header>
    {shape.message ? <p>{shape.message}</p> : null}
    <div className="constraint-shape-rules">
      {shape.constraints.map((constraint, index) => <ConstraintRule key={`node-${constraint.kind}-${index}`} constraint={constraint} context="Node" checked={checked} violated={findings.some((finding) => finding.shapeIri === shape.iri && finding.constraint === constraint.kind)} />)}
      {shape.propertyShapes.flatMap((propertyShape) => propertyShape.constraints.map((constraint, index) => <ConstraintRule key={`${propertyShape.iri}-${constraint.kind}-${index}`} constraint={constraint} context={propertyShape.path.label} checked={checked} violated={findings.some((finding) => finding.shapeIri === propertyShape.iri && finding.constraint === constraint.kind)} />))}
    </div>
    <details><summary>Technical details</summary><code>{shape.iri}</code></details>
  </article>;
}

function ConstraintRule({ constraint, context, checked, violated }: { constraint: WebShaclConstraintSummary; context: string; checked: boolean; violated: boolean }) {
  return <div className={`constraint-rule ${checked ? (violated ? "constraint-rule-violated" : "constraint-rule-valid") : "constraint-rule-unchecked"}`}><span>{context}</span><strong>{humanizeShaclName(constraint.kind)}</strong><span>{constraint.valueLabel ?? constraint.value ?? "Enabled"}</span><span className="constraint-rule-status" aria-label={checked ? (violated ? "Constraint violated" : "Constraint satisfied") : "Constraint not validated"}>{checked ? (violated ? "×" : "✓") : "—"}</span></div>;
}

function humanizeShaclTarget(value: string): string {
  return humanizeShaclName(value.replace(/^Target/, "Target "));
}

function humanizeShaclName(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^./, (character) => character.toUpperCase());
}

function Unavailable() { return <div className="empty-state"><h2>Source unavailable</h2><p>This workspace needs an ontology source before it can open.</p></div>; }

function SearchResults({ query, onOpen, allowedKinds, sourceVisible }: { query: ReturnType<typeof useProjectSearch>; onOpen: (entity: WebEntityReference) => void; allowedKinds: ReadonlySet<OntologyGraphNodeKind>; sourceVisible: boolean }) {
  if (query.isPending) return <p role="status">Searching...</p>;
  if (query.isError) return <p role="alert">Search unavailable.</p>;
  const results = sourceVisible ? (query.data?.page.items ?? []).filter((result) => allowedKinds.has(result.kind as OntologyGraphNodeKind)) : [];
  if (!results.length) return <p className="muted">No matching entities.</p>;
  return <ul className="search-results">{results.map((result) => {
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

function OutlineEntityList({ title, marker, items, loading, error, onOpen, onOpenDetails, onContextMenu, stagedIris }: { title: string; marker: "O" | "P"; items: WebOutlineItem[]; loading: boolean; error: boolean; onOpen: (entity: WebEntityReference) => void; onOpenDetails: (entity: WebEntityReference) => void; onContextMenu: (event: MouseEvent, entity: WebEntityReference) => void; stagedIris?: ReadonlySet<string> }) {
  return <section aria-label={title}>
    {error ? <p role="alert">{title} unavailable.</p> : null}
    {loading ? <p className="tree-status" role="status">Loading {title.toLowerCase()}...</p> : null}
    {!loading && !error && !items.length ? <p className="outline-empty">No {title.toLowerCase()}.</p> : null}
    {items.length ? <ul className="outline-entity-list">{items.map((item) => {
      const presentation = entityKindPresentation(item.kind);
      return <li className={stagedIris?.has(item.iri) ? "entity-staged" : undefined} key={`${item.sourceId}:${item.iri}`} onContextMenu={(event) => onContextMenu(event, item)}><button className="entity-link" type="button" aria-label={`${item.label}, ${presentation.label}`} onClick={() => onOpen(item)} onDoubleClick={() => onOpenDetails(item)}><span className={`entity-type-marker ${presentation.className}`} aria-hidden="true">{marker}</span><span className="entity-link-label">{item.label}</span><small>{presentation.label}</small></button></li>;
    })}</ul> : null}
  </section>;
}

function GroupedObjectOutline({ items, loading, error, onOpen, onOpenDetails, onContextMenu, stagedIris, collapsedGroupIris, onExpandedChange }: {
  items: WebOutlineItem[];
  loading: boolean;
  error: boolean;
  onOpen: (entity: WebEntityReference) => void;
  onOpenDetails: (entity: WebEntityReference) => void;
  onContextMenu: (event: MouseEvent, entity: WebEntityReference) => void;
  stagedIris?: ReadonlySet<string>;
  collapsedGroupIris: ReadonlySet<string>;
  onExpandedChange: (iri: string, expanded: boolean) => void;
}) {
  const groups = new Map<string, { type: WebEntityReference | null; items: WebOutlineItem[] }>();
  items.forEach((item) => {
    const key = item.directType?.iri ?? "__unclassified__";
    const group = groups.get(key) ?? { type: item.directType ?? null, items: [] };
    group.items.push(item);
    groups.set(key, group);
  });
  const sortedGroups = [...groups.values()]
    .map((group) => ({ ...group, items: [...group.items].sort(compareOutlineItems) }))
    .sort((left, right) => (left.type?.label ?? "Unclassified").localeCompare(right.type?.label ?? "Unclassified"));

  return <section aria-label="Objects">
    {error ? <p role="alert">Objects unavailable.</p> : null}
    {loading ? <p className="tree-status" role="status">Loading objects...</p> : null}
    {!loading && !error && !items.length ? <p className="outline-empty">No objects.</p> : null}
    {sortedGroups.length ? <ul className="object-group-list">{sortedGroups.map((group) => {
      const heading = group.type?.label ?? "Unclassified";
      const groupIri = group.type?.iri ?? "__unclassified__";
      const expanded = !collapsedGroupIris.has(groupIri);
      return <li className="object-group" key={groupIri}>
        <div className="object-group-heading">
          <div className="object-group-title">
            <button className="object-group-toggle" type="button" aria-label={`${expanded ? "Collapse" : "Expand"} ${heading} objects`} aria-expanded={expanded} onClick={() => onExpandedChange(groupIri, !expanded)}>
              <span className={`hierarchy-chevron ${expanded ? "hierarchy-chevron-expanded" : ""}`} aria-hidden="true" />
            </button>
            {group.type ? <button className="object-group-label" type="button" onClick={() => onOpen(group.type!)} onDoubleClick={() => onOpenDetails(group.type!)}>{heading}</button> : <span className="object-group-label">{heading}</span>}
          </div>
          <small>{group.items.length}</small>
        </div>
        {expanded ? <ul className="object-group-items">{group.items.map((item) => <li
          className={stagedIris?.has(item.iri) ? "entity-staged" : undefined}
          key={`${item.sourceId}:${item.iri}`}
          onContextMenu={(event) => onContextMenu(event, item)}
        >
          <button className="entity-link object-group-entity" type="button" aria-label={`${item.label}, Object`} onClick={() => onOpen(item)} onDoubleClick={() => onOpenDetails(item)}>
            <span className="object-group-elbow" aria-hidden="true" />
            <span className="entity-type-marker entity-type-object" aria-hidden="true">O</span>
            <span className="entity-link-label">{item.label}</span>
            <small>Object</small>
          </button>
        </li>)}</ul> : null}
      </li>;
    })}</ul> : null}
  </section>;
}

function compareOutlineItems(left: WebOutlineItem, right: WebOutlineItem): number {
  return left.label.localeCompare(right.label) || left.iri.localeCompare(right.iri);
}

function mergeOutlineEntities(applied: WebOutlineItem[], staged: StagedEntityReference[]): WebOutlineItem[] {
  const byIri = new Map<string, WebOutlineItem>();
  [...applied, ...staged].forEach((item) => byIri.set(item.iri, {
    iri: item.iri,
    label: item.label,
    kind: item.kind,
    sourceId: item.sourceId,
    directType: item.directType,
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
  const labels = stagedEntityLabelOverrides(entries);
  return entries.flatMap((entry) => {
    const editType = entry.summary.split(" · ")[0];
    const kind = kinds[editType];
    const iri = entry.generatedIris[0];
    const label = entry.normalizedValues.label ?? entry.normalizedValues.individualLabel;
    const directType = kind === "Individual" && entry.normalizedValues.classIri
      ? {
          iri: entry.normalizedValues.classIri,
          label: entry.normalizedValues.classLabel ?? iriLocalName(entry.normalizedValues.classIri),
          kind: "Class",
          sourceId: entry.sourceId,
        }
      : null;
    return kind && iri && label ? [{ iri, label: labels.get(iri) ?? label, kind, sourceId: entry.sourceId, directType }] : [];
  });
}

function stagedEntityLabelOverrides(entries: WebStagedEntry[]): Map<string, string> {
  const labels = new Map<string, string>();
  entries.forEach((entry) => {
    const editType = entry.editType || entry.summary.split(" · ")[0];
    const iri = entry.normalizedValues.resourceIri;
    const label = entry.normalizedValues.label;
    if (editType === "set-entity-label" && iri && label) labels.set(iri, label);
  });
  return labels;
}

function applyStagedLabels<T extends WebEntityReference>(items: readonly T[], labels: ReadonlyMap<string, string>): T[] {
  return items.map((item) => {
    const stagedLabel = labels.get(item.iri);
    return stagedLabel && stagedLabel !== item.label ? { ...item, label: stagedLabel } : item;
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
  stagedEntityLabelOverrides(entries).forEach((label, iri) => labels.set(iri, label));

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

function EmptyWorkspace({ onOpenMap }: { onOpenMap: () => void }) { return <div className="empty-workspace"><span className="overline">Explore</span><h1>Select an entity</h1><p>Choose a class from the outline or search by label to open its details in a tab.</p><button className="button primary" type="button" onClick={onOpenMap}>View Map</button></div>; }

function reorderTabs(tabs: EntityTab[], sourceIri: string, targetIri: string): EntityTab[] {
  const sourceIndex = tabs.findIndex((tab) => tab.iri === sourceIri);
  const targetIndex = tabs.findIndex((tab) => tab.iri === targetIri);
  if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return tabs;
  const next = [...tabs];
  const [moved] = next.splice(sourceIndex, 1);
  next.splice(targetIndex, 0, moved);
  return next;
}
