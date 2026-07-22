import type { WebEntityReference } from "../../web/projectApi";
import { useOntologyGraph } from "../../web/queries";
import OntologyGraphRenderer, { type RendererState } from "./OntologyGraphRenderer";

export type OntologyMapViewState = RendererState;

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
  return <section aria-label="Ontology map">
    <header><span>Read-only ontology map</span><strong>{graph.data.nodes.length} of {graph.data.totalNodeCount} entities</strong></header>
    <OntologyGraphRenderer nodes={graph.data.nodes} edges={graph.data.edges} state={state} onStateChange={onStateChange} onViewDetails={(node) => onViewDetails({ iri: node.identity.entityIri, label: node.label, kind: node.kind, sourceId: node.identity.sourceId })} />
  </section>;
}
