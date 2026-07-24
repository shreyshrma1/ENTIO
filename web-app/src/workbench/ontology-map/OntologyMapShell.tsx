import { useEffect, useMemo, useRef, type PointerEvent } from "react";
import type { OntologyGraphExpansionCategory, OntologyGraphEdgeKind, OntologyGraphNodeKind, WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";
import type { WebEntityReference } from "../../web/projectApi";
import { useOntologyGraph } from "../../web/queries";
import OntologyGraphRenderer, { type RendererState } from "./OntologyGraphRenderer";
import { classChildCounts, layeredGraphLayout } from "./graphLayout";

export interface OntologyMapViewState extends RendererState {
  nodes?: WebOntologyGraphNode[];
  edges?: WebOntologyGraphEdge[];
  graphFingerprint?: string;
  nodeKinds?: OntologyGraphNodeKind[];
  edgeKinds?: OntologyGraphEdgeKind[];
  expanded?: boolean;
  centerKey?: string;
  continuation?: { token: string; category: OntologyGraphExpansionCategory; entityId: string };
  sourceVisible?: boolean;
  stale?: boolean;
  layoutMode?: "Focus" | "FullMap";
  expandedClassIds?: string[];
  revealedIndividualIds?: string[];
  popupPosition?: { x: number; y: number };
  popupCoordinateSpace?: "graph";
}

const LARGE_BRANCH_LIMIT = 6;
const OUTSIDE_CLICK_THRESHOLD = 4;

export default function OntologyMapShell({ projectId, sourceId, seed, state, onStateChange, onViewDetails, onLoadedEntities, includeAppliedInferred = false, includeProposalInferred = false }: {
  projectId: string;
  sourceId: string;
  seed?: WebEntityReference;
  state: OntologyMapViewState;
  onStateChange: (state: OntologyMapViewState) => void;
  onViewDetails: (entity: WebEntityReference) => void;
  onLoadedEntities?: (entities: Record<string, string>) => void;
  includeAppliedInferred?: boolean;
  includeProposalInferred?: boolean;
}) {
  const popupRef = useRef<HTMLElement>(null);
  const popupDrag = useRef<{ pointerId: number; clientX: number; clientY: number; x: number; y: number; latestX: number; latestY: number } | null>(null);
  const outsidePointer = useRef<{ pointerId: number; clientX: number; clientY: number } | null>(null);
  const graph = useOntologyGraph(projectId, {
    sourceIds: [sourceId],
    seed: seed?.sourceId ? { sourceId: seed.sourceId, entityIri: seed.iri } : undefined,
    includeAppliedInferred,
    includeProposalInferred,
  });

  useEffect(() => {
    const centerKey = `${seed?.sourceId ? `${seed.sourceId}\u0000${seed.iri}` : "root"}|${includeAppliedInferred}|${includeProposalInferred}`;
    if (!graph.data || (state.graphFingerprint === graph.data.graphFingerprint && state.centerKey === centerKey)) return;
    if (state.graphFingerprint && state.graphFingerprint !== graph.data.graphFingerprint) {
      if (!state.stale) onStateChange({ ...state, stale: true });
      return;
    }
    onStateChange({ ...state, selectedNodeId: graph.data.seed?.id ?? null, nodes: graph.data.nodes, edges: graph.data.edges, graphFingerprint: graph.data.graphFingerprint, centerKey });
  }, [graph.data, includeAppliedInferred, includeProposalInferred, onStateChange, seed, state.centerKey, state.graphFingerprint]);

  const nodes = state.nodes ?? graph.data?.nodes ?? [];
  const edges = state.edges ?? graph.data?.edges ?? [];
  useEffect(() => {
    onLoadedEntities?.(Object.fromEntries(nodes.map((node) => [`${node.identity.sourceId}\u0000${node.identity.entityIri}`, node.identity.id])));
  }, [nodes, onLoadedEntities]);

  const assertedEdges = useMemo(() => edges.filter((edge) => edge.provenance === "Asserted"), [edges]);
  const childCounts = useMemo(() => classChildCounts(nodes, assertedEdges), [nodes, assertedEdges]);
  const basePositions = useMemo(() => layeredGraphLayout(nodes, assertedEdges), [nodes, assertedEdges]);
  const revealedIndividualIds = useMemo(() => state.nodeKinds?.includes("Individual") ? new Set(nodes.filter((node) => node.kind === "Individual").map((node) => node.identity.id)) : new Set(state.revealedIndividualIds ?? []), [nodes, state.nodeKinds, state.revealedIndividualIds]);
  const projectedIds = useMemo(() => projectedNodeIds(nodes, edges, state.layoutMode ?? "FullMap", state.selectedNodeId, new Set(state.expandedClassIds ?? []), revealedIndividualIds, basePositions), [basePositions, edges, nodes, revealedIndividualIds, state.expandedClassIds, state.layoutMode, state.selectedNodeId]);
  const visibleNodes = useMemo(() => state.sourceVisible === false ? [] : nodes.filter((node) => projectedIds.has(node.identity.id) && (state.nodeKinds === undefined || state.nodeKinds.includes(node.kind))), [nodes, projectedIds, state.nodeKinds, state.sourceVisible]);
  const visibleIds = useMemo(() => new Set(visibleNodes.map((node) => node.identity.id)), [visibleNodes]);
  const visibleEdges = useMemo(() => edges.filter((edge) => visibleIds.has(edge.sourceNodeId) && visibleIds.has(edge.targetNodeId) && (state.edgeKinds === undefined || state.edgeKinds.includes(edge.kind))), [edges, state.edgeKinds, visibleIds]);
  const selected = nodes.find((node) => node.identity.id === state.selectedNodeId);
  useEffect(() => {
    if (!selected) return;
    const trackOutsidePointer = (event: globalThis.PointerEvent) => {
      const target = event.target as Element;
      const insideMap = target.closest(".ontology-graph-viewport");
      const protectedControl = target.closest(".ontology-node-popup, .ontology-map-legend");
      outsidePointer.current = insideMap && !protectedControl
        ? { pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY }
        : null;
    };
    const dismissOutsideClick = (event: globalThis.PointerEvent) => {
      const start = outsidePointer.current;
      outsidePointer.current = null;
      if (!start || start.pointerId !== event.pointerId) return;
      if (Math.hypot(event.clientX - start.clientX, event.clientY - start.clientY) > OUTSIDE_CLICK_THRESHOLD) return;
      onStateChange({ ...state, selectedNodeId: null });
    };
    const cancelOutsidePointer = () => { outsidePointer.current = null; };
    document.addEventListener("pointerdown", trackOutsidePointer);
    document.addEventListener("pointerup", dismissOutsideClick);
    document.addEventListener("pointercancel", cancelOutsidePointer);
    return () => {
      document.removeEventListener("pointerdown", trackOutsidePointer);
      document.removeEventListener("pointerup", dismissOutsideClick);
      document.removeEventListener("pointercancel", cancelOutsidePointer);
    };
  }, [onStateChange, selected, state]);
  useEffect(() => {
    if (!selected || !state.popupPosition || state.popupCoordinateSpace !== "graph") return;
    const frame = popupRef.current?.closest<HTMLElement>(".ontology-graph-viewport-overlay");
    const positioner = popupRef.current?.closest<HTMLElement>(".ontology-graph-viewport-overlay-position");
    const popup = popupRef.current;
    if (!frame || !positioner || !popup) return;
    const frameWidth = frame.clientWidth;
    const frameHeight = frame.clientHeight;
    if (!frameWidth || !frameHeight) return;
    const x = Math.max(0, Math.min(frameWidth - popup.offsetWidth, state.popupPosition.x));
    const y = Math.max(0, Math.min(frameHeight - popup.offsetHeight, state.popupPosition.y));
    positioner.style.left = `${x}px`;
    positioner.style.top = `${y}px`;
    if (x !== state.popupPosition.x || y !== state.popupPosition.y) {
      onStateChange({ ...state, popupPosition: { x, y }, popupCoordinateSpace: "graph" });
    }
  }, [onStateChange, selected, state]);
  const emphasizedIds = useMemo(() => selectionNeighborhood(state.selectedNodeId, nodes, edges), [edges, nodes, state.selectedNodeId]);
  const dimmedNodeIds = useMemo(() => state.selectedNodeId ? new Set(visibleNodes.map((node) => node.identity.id).filter((id) => !emphasizedIds.has(id))) : new Set<string>(), [emphasizedIds, state.selectedNodeId, visibleNodes]);
  const dimmedEdgeIds = useMemo(() => state.selectedNodeId ? new Set(visibleEdges.filter((edge) => !(emphasizedIds.has(edge.sourceNodeId) && emphasizedIds.has(edge.targetNodeId))).map((edge) => edge.id)) : new Set<string>(), [emphasizedIds, state.selectedNodeId, visibleEdges]);

  if (graph.isPending) return <section aria-label="Ontology map"><p role="status">Loading ontology map...</p></section>;
  if (graph.isError && graph.error instanceof DOMException && graph.error.name === "AbortError") return <section aria-label="Ontology map"><p>Loading was cancelled.</p></section>;
  if (graph.isError) return <section aria-label="Ontology map"><p role="alert">Could not load the ontology map. No project data was changed.</p><button type="button" onClick={() => void graph.refetch()}>Retry</button></section>;
  if (!nodes.length) return <section aria-label="Ontology map"><p>No supported local entities are available for this map.</p></section>;
  async function refreshStaleGraph() {
    const filters = { nodeKinds: state.nodeKinds, edgeKinds: state.edgeKinds, sourceVisible: state.sourceVisible };
    onStateChange({ selectedNodeId: null, ...filters });
    await graph.refetch();
    requestAnimationFrame(() => document.querySelector<HTMLElement>(".ontology-graph-viewport")?.focus());
  }
  function startPopupDrag(event: PointerEvent<HTMLElement>) {
    if ((event.target as HTMLElement).closest("button")) return;
    event.stopPropagation();
    const popup = popupRef.current;
    const positioner = popup?.closest<HTMLElement>(".ontology-graph-viewport-overlay-position");
    const viewport = popup?.closest<HTMLElement>(".ontology-graph-viewport-overlay");
    if (!popup || !positioner || !viewport) return;
    const popupBounds = popup.getBoundingClientRect();
    const viewportBounds = viewport.getBoundingClientRect();
    const x = popupBounds.left - viewportBounds.left;
    const y = popupBounds.top - viewportBounds.top;
    event.currentTarget.setPointerCapture(event.pointerId);
    positioner.style.left = `${x}px`;
    positioner.style.top = `${y}px`;
    popupDrag.current = { pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY, x, y, latestX: x, latestY: y };
  }
  function movePopup(event: PointerEvent<HTMLElement>) {
    event.stopPropagation();
    const drag = popupDrag.current;
    const popup = popupRef.current;
    const positioner = popup?.closest<HTMLElement>(".ontology-graph-viewport-overlay-position");
    const viewport = popup?.closest<HTMLElement>(".ontology-graph-viewport-overlay");
    if (!drag || drag.pointerId !== event.pointerId || !popup || !viewport) return;
    const x = Math.max(0, Math.min(viewport.clientWidth - popup.offsetWidth, drag.x + event.clientX - drag.clientX));
    const y = Math.max(0, Math.min(viewport.clientHeight - popup.offsetHeight, drag.y + event.clientY - drag.clientY));
    if (!positioner) return;
    positioner.style.left = `${x}px`;
    positioner.style.top = `${y}px`;
    drag.latestX = x;
    drag.latestY = y;
  }
  function stopPopupDrag(event?: PointerEvent<HTMLElement>) {
    event?.stopPropagation();
    const drag = popupDrag.current;
    popupDrag.current = null;
    if (drag) onStateChange({ ...state, popupPosition: { x: drag.latestX, y: drag.latestY }, popupCoordinateSpace: "graph" });
  }
  const selectedCard = selected ? <aside ref={popupRef} className="ontology-node-popup" role="dialog" aria-label={`${selected.label} map summary`}>
      <button className="ontology-node-popup-close" type="button" aria-label="Close entity summary" onClick={() => onStateChange({ ...state, selectedNodeId: null })}>×</button>
      <header className="ontology-node-popup-drag-handle" onPointerDown={startPopupDrag} onPointerMove={movePopup} onPointerUp={stopPopupDrag} onPointerCancel={stopPopupDrag}><h3>{selected.label}</h3><p>{selected.kind} · Asserted</p></header>
      {selected.definitionExcerpt ? <p className="ontology-node-popup-definition">{selected.definitionExcerpt}</p> : null}
      <section className="ontology-node-popup-details" aria-labelledby="ontology-node-popup-details-title">
        <h4 id="ontology-node-popup-details-title">Details</h4>
        <p><strong>Direct subclasses:</strong> {childCounts[selected.identity.id] ?? 0}</p>
        <p><strong>Loaded relationships:</strong> {selected.summary.loadedRelationshipCount}</p>
        <p><strong>Available relationships:</strong> {selected.summary.availableRelationshipCount}</p>
      </section>
      <button className="ontology-node-popup-view" type="button" onClick={() => onViewDetails({ iri: selected.identity.entityIri, label: selected.label, kind: selected.kind, sourceId: selected.identity.sourceId })}>View Details</button>
    </aside> : null;
  return <section className="ontology-map-shell" aria-label="Ontology map">
    <OntologyGraphRenderer nodes={visibleNodes} edges={visibleEdges} state={state} toolbarStart={<fieldset disabled={state.stale} className="ontology-map-actions"><legend className="visually-hidden">Current ontology map actions</legend><div className="ontology-layout-modes" role="group" aria-label="Map layout mode">{(["Focus", "FullMap"] as const).map((mode) => <button aria-pressed={(state.layoutMode ?? "FullMap") === mode} className={(state.layoutMode ?? "FullMap") === mode ? "active" : ""} key={mode} type="button" onClick={() => onStateChange({ ...state, layoutMode: mode })}>{mode === "FullMap" ? "Full map" : mode}</button>)}</div></fieldset>} viewportOverlay={selected && selectedCard ? { position: state.popupCoordinateSpace === "graph" ? state.popupPosition : undefined, content: selectedCard } : undefined} childCounts={childCounts} dimmedNodeIds={dimmedNodeIds} dimmedEdgeIds={dimmedEdgeIds} onStateChange={onStateChange} />
    {state.stale ? <div className="ontology-map-stale" role="alertdialog" aria-modal="true" aria-labelledby="ontology-map-stale-title"><h3 id="ontology-map-stale-title">Ontology map is out of date</h3><p>The displayed map is preserved for reference. Refresh before loading or opening current project data.</p><button type="button" autoFocus onClick={() => void refreshStaleGraph()}>Refresh map</button></div> : null}
    <p className="visually-hidden" role="status" aria-live="polite">{state.stale ? "Ontology map is stale" : ""}</p>
  </section>;
}

export function projectedNodeIds(nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[], mode: "Hierarchy" | "Focus" | "FullMap", selectedId: string | null, expanded: Set<string>, revealedIndividuals: Set<string>, positions: Record<string, { x: number; y: number }>): Set<string> {
  const byId = new Map(nodes.map((node) => [node.identity.id, node]));
  if (mode === "Focus" && selectedId) return new Set([...selectionNeighborhood(selectedId, nodes, edges)].filter((id) => byId.get(id)?.kind !== "Individual" || revealedIndividuals.has(id)));
  if (mode === "Focus") return new Set(nodes.filter((node) => node.kind !== "Individual" || revealedIndividuals.has(node.identity.id)).map((node) => node.identity.id));
  const classes = nodes.filter((node) => node.kind === "Class");
  const classIds = new Set(classes.map((node) => node.identity.id));
  const children = new Map(classes.map((node) => [node.identity.id, [] as string[]]));
  const hasParent = new Set<string>();
  edges.filter((edge) => edge.provenance === "Asserted" && edge.kind === "SubclassOf" && classIds.has(edge.sourceNodeId) && classIds.has(edge.targetNodeId)).forEach((edge) => { children.get(edge.targetNodeId)!.push(edge.sourceNodeId); hasParent.add(edge.sourceNodeId); });
  children.forEach((items) => items.sort((a, b) => (positions[a]?.y ?? 0) - (positions[b]?.y ?? 0) || a.localeCompare(b)));
  const visible = new Set<string>();
  function visit(id: string) {
    if (visible.has(id)) return;
    visible.add(id);
    const branch = children.get(id) ?? [];
    (mode === "FullMap" || expanded.has(id) ? branch : branch.slice(0, LARGE_BRANCH_LIMIT)).forEach(visit);
  }
  const roots = classes.filter((node) => !hasParent.has(node.identity.id)).sort((a, b) => (positions[a.identity.id]?.y ?? 0) - (positions[b.identity.id]?.y ?? 0));
  const structurallyReachable = new Set<string>();
  function markReachable(id: string) { if (structurallyReachable.has(id)) return; structurallyReachable.add(id); (children.get(id) ?? []).forEach(markReachable); }
  roots.forEach((node) => { markReachable(node.identity.id); visit(node.identity.id); });
  classes.filter((node) => !structurallyReachable.has(node.identity.id)).forEach((node) => visit(node.identity.id));
  nodes.filter((node) => node.kind !== "Class" && node.kind !== "Individual").forEach((node) => {
    const domains = edges.filter((edge) => edge.sourceNodeId === node.identity.id && edge.kind === "Domain");
    if (mode === "FullMap" || !domains.length || domains.some((edge) => visible.has(edge.targetNodeId))) visible.add(node.identity.id);
  });
  revealedIndividuals.forEach((id) => visible.add(id));
  if (selectedId) selectionNeighborhood(selectedId, nodes, edges).forEach((id) => { if (byId.get(id)?.kind !== "Individual" || revealedIndividuals.has(id)) visible.add(id); });
  return visible;
}

export function selectionNeighborhood(selectedId: string | null, nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[]): Set<string> {
  if (!selectedId) return new Set(nodes.filter((node) => node.kind !== "Individual").map((node) => node.identity.id));
  const related = new Set([selectedId]);
  edges.filter((edge) => edge.sourceNodeId === selectedId || edge.targetNodeId === selectedId).forEach((edge) => { related.add(edge.sourceNodeId); related.add(edge.targetNodeId); });
  const propertyIds = [...related].filter((id) => nodes.find((node) => node.identity.id === id)?.kind === "ObjectProperty");
  edges.filter((edge) => propertyIds.includes(edge.sourceNodeId)).forEach((edge) => related.add(edge.targetNodeId));
  return related;
}
