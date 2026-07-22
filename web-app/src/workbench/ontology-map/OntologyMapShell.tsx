import type { WebEntityReference } from "../../web/projectApi";
import { useOntologyGraph } from "../../web/queries";

export interface OntologyMapViewState { selectedNodeId: string | null }

export default function OntologyMapShell({
  projectId, sourceId, seed, state, onStateChange, onViewDetails,
}: {
  projectId: string;
  sourceId: string;
  seed?: WebEntityReference;
  state: OntologyMapViewState;
  onStateChange: (state: OntologyMapViewState) => void;
  onViewDetails: (entity: WebEntityReference) => void;
}) {
  const graph = useOntologyGraph(projectId, {
    sourceIds: [sourceId],
    seed: seed?.sourceId ? { sourceId: seed.sourceId, entityIri: seed.iri } : undefined,
  });

  if (graph.isPending) return <section aria-label="Ontology map"><p role="status">Loading ontology map...</p></section>;
  if (graph.isError) return <section aria-label="Ontology map"><p role="alert">Could not load the ontology map.</p><button type="button" onClick={() => void graph.refetch()}>Retry</button></section>;
  if (!graph.data.nodes.length) return <section aria-label="Ontology map"><p>No supported local entities are available for this map.</p></section>;
  const selected = graph.data.nodes.find((node) => node.identity.id === state.selectedNodeId) ?? graph.data.nodes[0];
  return <section aria-label="Ontology map">
    <header><span>Read-only ontology map</span><strong>{graph.data.nodes.length} of {graph.data.totalNodeCount} entities</strong></header>
    <p>The interactive graph renderer is added in the next implementation slice.</p>
    <label>Preview entity <select value={selected.identity.id} onChange={(event) => onStateChange({ selectedNodeId: event.target.value })}>{graph.data.nodes.map((node) => <option key={node.identity.id} value={node.identity.id}>{node.label}</option>)}</select></label>
    <button type="button" onClick={() => onViewDetails({ iri: selected.identity.entityIri, label: selected.label, kind: selected.kind, sourceId: selected.identity.sourceId })}>View Details</button>
  </section>;
}
