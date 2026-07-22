import { useEffect, useMemo, useState } from "react";
import type { OntologyGraphExpansionCategory, OntologyGraphEdgeKind, OntologyGraphNodeKind, WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";
import { loadOntologyGraphNeighborhood, WebApiError, type WebEntityReference } from "../../web/projectApi";
import { useOntologyGraph } from "../../web/queries";
import OntologyGraphRenderer, { type RendererState } from "./OntologyGraphRenderer";
import { mergeGraphPage } from "./graphMerge";
import { classChildCounts, layeredGraphLayout, positionsForExpansion } from "./graphLayout";

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
  layoutMode?: "Hierarchy" | "Focus" | "FullMap";
  expandedClassIds?: string[];
  revealedIndividualIds?: string[];
}

const LARGE_BRANCH_LIMIT = 6;

const expansionCategories: Array<{ value: OntologyGraphExpansionCategory; label: string }> = [
  { value: "ClassHierarchy", label: "Class hierarchy" },
  { value: "PropertySchema", label: "Property schema" },
  { value: "AssertedTypes", label: "Asserted types" },
  { value: "ObjectAssertions", label: "Object assertions" },
];

export default function OntologyMapShell({ projectId, sourceId, seed, state, onStateChange, onViewDetails, onLoadedEntities }: {
  projectId: string;
  sourceId: string;
  seed?: WebEntityReference;
  state: OntologyMapViewState;
  onStateChange: (state: OntologyMapViewState) => void;
  onViewDetails: (entity: WebEntityReference) => void;
  onLoadedEntities?: (entities: Record<string, string>) => void;
}) {
  const graph = useOntologyGraph(projectId, { sourceIds: [sourceId], seed: seed?.sourceId ? { sourceId: seed.sourceId, entityIri: seed.iri } : undefined });
  const [expanding, setExpanding] = useState(false);
  const [partialMessage, setPartialMessage] = useState<string | null>(null);

  useEffect(() => {
    const centerKey = seed?.sourceId ? `${seed.sourceId}\u0000${seed.iri}` : "root";
    if (!graph.data || (state.graphFingerprint === graph.data.graphFingerprint && state.centerKey === centerKey)) return;
    if (state.graphFingerprint && state.graphFingerprint !== graph.data.graphFingerprint) {
      if (!state.stale) onStateChange({ ...state, stale: true });
      return;
    }
    onStateChange({ selectedNodeId: graph.data.seed?.id ?? null, nodes: graph.data.nodes, edges: graph.data.edges, graphFingerprint: graph.data.graphFingerprint, centerKey });
  }, [graph.data, onStateChange, seed, state.centerKey, state.graphFingerprint]);

  const nodes = state.nodes ?? graph.data?.nodes ?? [];
  const edges = state.edges ?? graph.data?.edges ?? [];
  useEffect(() => {
    onLoadedEntities?.(Object.fromEntries(nodes.map((node) => [`${node.identity.sourceId}\u0000${node.identity.entityIri}`, node.identity.id])));
  }, [nodes, onLoadedEntities]);

  const childCounts = useMemo(() => classChildCounts(nodes, edges), [nodes, edges]);
  const basePositions = useMemo(() => layeredGraphLayout(nodes, edges), [nodes, edges]);
  const projectedIds = useMemo(() => projectedNodeIds(nodes, edges, state.layoutMode ?? "Hierarchy", state.selectedNodeId, new Set(state.expandedClassIds ?? []), new Set(state.revealedIndividualIds ?? []), basePositions), [basePositions, edges, nodes, state.expandedClassIds, state.layoutMode, state.revealedIndividualIds, state.selectedNodeId]);
  const visibleNodes = useMemo(() => state.sourceVisible === false ? [] : nodes.filter((node) => projectedIds.has(node.identity.id) && (state.nodeKinds === undefined || state.nodeKinds.includes(node.kind))), [nodes, projectedIds, state.nodeKinds, state.sourceVisible]);
  const visibleIds = useMemo(() => new Set(visibleNodes.map((node) => node.identity.id)), [visibleNodes]);
  const visibleEdges = useMemo(() => edges.filter((edge) => visibleIds.has(edge.sourceNodeId) && visibleIds.has(edge.targetNodeId) && (state.edgeKinds === undefined || state.edgeKinds.includes(edge.kind))), [edges, state.edgeKinds, visibleIds]);
  const selected = nodes.find((node) => node.identity.id === state.selectedNodeId);
  const emphasizedIds = useMemo(() => selectionNeighborhood(state.selectedNodeId, nodes, edges), [edges, nodes, state.selectedNodeId]);
  const dimmedNodeIds = useMemo(() => state.selectedNodeId ? new Set(visibleNodes.map((node) => node.identity.id).filter((id) => !emphasizedIds.has(id))) : new Set<string>(), [emphasizedIds, state.selectedNodeId, visibleNodes]);
  const dimmedEdgeIds = useMemo(() => state.selectedNodeId ? new Set(visibleEdges.filter((edge) => !(emphasizedIds.has(edge.sourceNodeId) && emphasizedIds.has(edge.targetNodeId))).map((edge) => edge.id)) : new Set<string>(), [emphasizedIds, state.selectedNodeId, visibleEdges]);

  async function expand(category: OntologyGraphExpansionCategory, continuation?: string) {
    if (!selected || !state.graphFingerprint || expanding) return;
    setExpanding(true);
    setPartialMessage(null);
    try {
      const page = await loadOntologyGraphNeighborhood(projectId, { sourceIds: [sourceId], entity: { sourceId: selected.identity.sourceId, entityIri: selected.identity.entityIri }, categories: [category], expectedFingerprint: state.graphFingerprint, continuation });
      const merged = mergeGraphPage(nodes, edges, page.nodes, page.edges, 300, 600);
      const revealedIndividualIds = category === "AssertedTypes" || category === "ObjectAssertions" ? [...new Set([...(state.revealedIndividualIds ?? []), ...page.nodes.filter((node) => node.kind === "Individual").map((node) => node.identity.id)])] : state.revealedIndividualIds;
      onStateChange({ ...state, nodes: merged.nodes, edges: merged.edges, positions: positionsForExpansion(state.positions ?? basePositions, new Set(nodes.map((node) => node.identity.id)), merged.nodes, merged.edges), revealedIndividualIds, expanded: true, continuation: page.continuation ? { token: page.continuation, category, entityId: selected.identity.id } : undefined });
      setPartialMessage(merged.limited ? "The map reached the 300-entity or 600-relationship tab limit." : page.continuation ? "More relationships are available from the server." : "Neighborhood loaded.");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      if (error instanceof WebApiError && error.code === "stale-graph-fingerprint") {
        onStateChange({ ...state, stale: true });
        setPartialMessage(null);
      } else {
        setPartialMessage("Could not load this neighborhood. Existing map data was kept. Retry when the project is available.");
      }
    } finally { setExpanding(false); }
  }

  if (graph.isPending) return <section aria-label="Ontology map"><p role="status">Loading ontology map...</p></section>;
  if (graph.isError && graph.error instanceof DOMException && graph.error.name === "AbortError") return <section aria-label="Ontology map"><p>Loading was cancelled.</p></section>;
  if (graph.isError) return <section aria-label="Ontology map"><p role="alert">Could not load the ontology map. No project data was changed.</p><button type="button" onClick={() => void graph.refetch()}>Retry</button></section>;
  if (!nodes.length) return <section aria-label="Ontology map"><p>No supported local entities are available for this map.</p></section>;
  async function refreshStaleGraph() {
    const filters = { nodeKinds: state.nodeKinds, edgeKinds: state.edgeKinds, sourceVisible: state.sourceVisible };
    onStateChange({ selectedNodeId: null, ...filters });
    setPartialMessage(null);
    await graph.refetch();
    requestAnimationFrame(() => document.querySelector<HTMLElement>(".ontology-graph-viewport")?.focus());
  }
  return <section className="ontology-map-shell" aria-label="Ontology map">
    <header><span>Read-only ontology map</span><strong>{nodes.length} loaded entities</strong></header>
    <fieldset disabled={state.stale} className="ontology-map-actions"><legend className="visually-hidden">Current ontology map actions</legend><div className="ontology-layout-modes" role="group" aria-label="Map layout mode">{(["Hierarchy", "Focus", "FullMap"] as const).map((mode) => <button className={(state.layoutMode ?? "Hierarchy") === mode ? "active" : ""} key={mode} type="button" onClick={() => onStateChange({ ...state, layoutMode: mode })}>{mode === "FullMap" ? "Full map" : mode}</button>)}</div><details className="ontology-map-filters"><summary>Filters</summary><fieldset><legend>Local sources</legend><label><input type="checkbox" checked={state.sourceVisible !== false} onChange={() => onStateChange({ ...state, sourceVisible: state.sourceVisible === false })} />{sourceId}</label></fieldset><fieldset><legend>Node kinds</legend>{(["Class", "ObjectProperty", "DatatypeProperty", "Individual"] as OntologyGraphNodeKind[]).map((kind) => <label key={kind}><input type="checkbox" checked={kind === "Individual" ? Boolean(state.revealedIndividualIds?.length) : state.nodeKinds === undefined || state.nodeKinds.includes(kind)} onChange={() => { if (kind === "Individual") { onStateChange({ ...state, revealedIndividualIds: state.revealedIndividualIds?.length ? [] : nodes.filter((node) => node.kind === "Individual").map((node) => node.identity.id) }); return; } const current = state.nodeKinds ?? ["Class", "ObjectProperty", "DatatypeProperty"]; const next = current.includes(kind) ? current.filter((item) => item !== kind) : [...current, kind]; onStateChange({ ...state, nodeKinds: next }); }} />{kind}</label>)}</fieldset><fieldset><legend>Relationships</legend>{(["SubclassOf", "Domain", "Range", "Type", "ObjectAssertion"] as OntologyGraphEdgeKind[]).map((kind) => <label key={kind}><input type="checkbox" checked={state.edgeKinds === undefined || state.edgeKinds.includes(kind)} onChange={() => { const current = state.edgeKinds ?? ["SubclassOf", "Domain", "Range", "Type", "ObjectAssertion"]; const next = current.includes(kind) ? current.filter((item) => item !== kind) : [...current, kind]; onStateChange({ ...state, edgeKinds: next }); }} />{kind}</label>)}</fieldset><button type="button" onClick={() => onStateChange({ ...state, sourceVisible: undefined, nodeKinds: undefined, edgeKinds: undefined, revealedIndividualIds: [] })}>Clear filters</button></details></fieldset>
    <OntologyGraphRenderer nodes={visibleNodes} edges={visibleEdges} state={state} childCounts={childCounts} dimmedNodeIds={dimmedNodeIds} dimmedEdgeIds={dimmedEdgeIds} onStateChange={onStateChange} onViewDetails={(node) => onViewDetails({ iri: node.identity.entityIri, label: node.label, kind: node.kind, sourceId: node.identity.sourceId })} />
    {selected ? <aside className="ontology-node-popup" role="dialog" aria-label={`${selected.label} map summary`}><button type="button" aria-label="Close entity summary" onClick={() => onStateChange({ ...state, selectedNodeId: null })}>×</button><span>{selected.kind} · Asserted</span><h3>{selected.label}</h3>{selected.definitionExcerpt ? <p>{selected.definitionExcerpt}</p> : null}<dl>{selected.kind === "Class" ? <><dt>Direct subclasses</dt><dd>{childCounts[selected.identity.id] ?? 0}</dd></> : null}<dt>Loaded relationships</dt><dd>{selected.summary.loadedRelationshipCount}</dd><dt>Available relationships</dt><dd>{selected.summary.availableRelationshipCount}</dd></dl>{selected.kind === "Class" && (childCounts[selected.identity.id] ?? 0) > LARGE_BRANCH_LIMIT ? <button type="button" onClick={() => { const expanded = new Set(state.expandedClassIds ?? []); if (expanded.has(selected.identity.id)) expanded.delete(selected.identity.id); else expanded.add(selected.identity.id); onStateChange({ ...state, expandedClassIds: [...expanded] }); }}>{new Set(state.expandedClassIds ?? []).has(selected.identity.id) ? "Collapse children" : `Expand ${childCounts[selected.identity.id]} children`}</button> : null}{selected.kind === "Class" ? <button type="button" onClick={() => onStateChange({ ...state, revealedIndividualIds: [...new Set([...(state.revealedIndividualIds ?? []), ...edges.filter((edge) => edge.kind === "Type" && edge.targetNodeId === selected.identity.id).map((edge) => edge.sourceNodeId)])] })}>Show individuals</button> : null}<div>{expansionCategories.map((category) => <button key={category.value} type="button" disabled={expanding} onClick={() => void expand(category.value)}>{category.label}</button>)}</div>{state.continuation?.entityId === selected.identity.id ? <button type="button" disabled={expanding} onClick={() => void expand(state.continuation!.category, state.continuation!.token)}>Load more</button> : null}<button type="button" onClick={() => onViewDetails({ iri: selected.identity.entityIri, label: selected.label, kind: selected.kind, sourceId: selected.identity.sourceId })}>View Details</button></aside> : null}
    {expanding ? <p role="status">Loading neighborhood...</p> : null}{partialMessage ? <p role="status">{partialMessage}</p> : null}
    {state.stale ? <div className="ontology-map-stale" role="alertdialog" aria-modal="true" aria-labelledby="ontology-map-stale-title"><h3 id="ontology-map-stale-title">Ontology map is out of date</h3><p>The displayed map is preserved for reference. Refresh before loading or opening current project data.</p><button type="button" autoFocus onClick={() => void refreshStaleGraph()}>Refresh map</button></div> : null}
    <p className="visually-hidden" role="status" aria-live="polite">{expanding ? "Loading ontology neighborhood" : partialMessage ?? (state.stale ? "Ontology map is stale" : "")}</p>
  </section>;
}

export function projectedNodeIds(nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[], mode: "Hierarchy" | "Focus" | "FullMap", selectedId: string | null, expanded: Set<string>, revealedIndividuals: Set<string>, positions: Record<string, { x: number; y: number }>): Set<string> {
  const byId = new Map(nodes.map((node) => [node.identity.id, node]));
  if (mode === "Focus" && selectedId) return new Set([...selectionNeighborhood(selectedId, nodes, edges)].filter((id) => byId.get(id)?.kind !== "Individual" || revealedIndividuals.has(id)));
  const classes = nodes.filter((node) => node.kind === "Class");
  const classIds = new Set(classes.map((node) => node.identity.id));
  const children = new Map(classes.map((node) => [node.identity.id, [] as string[]]));
  const hasParent = new Set<string>();
  edges.filter((edge) => edge.kind === "SubclassOf" && classIds.has(edge.sourceNodeId) && classIds.has(edge.targetNodeId)).forEach((edge) => { children.get(edge.targetNodeId)!.push(edge.sourceNodeId); hasParent.add(edge.sourceNodeId); });
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
