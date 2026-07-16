import { useEntityDetails } from "../web/queries";
import type { WebEntityReference, WebRdfValue, WebRelationship } from "../web/projectApi";
import StatusBadge from "../components/ui/StatusBadge";

interface EntityDetailsProps {
  projectId: string;
  iri: string;
}

export default function EntityDetails({ projectId, iri }: EntityDetailsProps) {
  const details = useEntityDetails(projectId, iri);

  if (details.isPending) return <p role="status">Loading entity details...</p>;
  if (details.isError) return <p role="alert">Could not load this entity. {details.error.message}</p>;
  const entity = details.data;

  return (
    <article className="entity-details">
      <div className="entity-heading">
        <div>
          <p className="eyebrow">{entity.kind}</p>
          <h2>{entity.label}</h2>
        </div>
        <div className="entity-statuses">
          <StatusBadge tone={entity.locality === "External" ? "external" : "asserted"}>{entity.locality}</StatusBadge>
          {details.isFetching ? <StatusBadge tone="neutral">Refreshing</StatusBadge> : null}
        </div>
      </div>
      <p className="entity-meta">Source: {entity.sourceId} · {entity.locality.toLowerCase()}</p>
      {entity.definitions.length ? (
        <section className="detail-section" aria-labelledby="definitions-heading">
          <h3 id="definitions-heading">Definition</h3>
          {entity.definitions.map((definition) => <p key={`${definition.value}:${definition.language ?? ""}`}>{definition.value}</p>)}
        </section>
      ) : null}
      <DetailReferences title="Types" items={entity.assertedTypes} />
      <DetailReferences title="Superclasses" items={entity.directSuperclasses} />
      <DetailReferences title="Subclasses" items={entity.directSubclasses} />
      <DetailReferences title="Domains" items={entity.domains} />
      <DetailReferences title="Ranges" items={entity.ranges} />
      <RelationshipSection title="Outgoing relationships" relationships={entity.outgoingRelationships} />
      <RelationshipSection title="Incoming relationships" relationships={entity.incomingRelationships} />
      <details className="technical-details">
        <summary>Technical details</summary>
        <dl>
          <div><dt>IRI</dt><dd><code>{entity.iri}</code></dd></div>
          <div><dt>Preferred label source</dt><dd>{entity.preferredLabelSource}</dd></div>
          {entity.sourceOntologyId ? <div><dt>Source ontology</dt><dd>{entity.sourceOntologyId}</dd></div> : null}
        </dl>
      </details>
    </article>
  );
}

function DetailReferences({ title, items }: { title: string; items: WebEntityReference[] }) {
  if (!items.length) return null;
  return (
    <section className="detail-section" aria-labelledby={`${title.toLowerCase().replaceAll(" ", "-")}-heading`}>
      <h3 id={`${title.toLowerCase().replaceAll(" ", "-")}-heading`}>{title}</h3>
      <ul className="reference-list">{items.map((item) => <li key={item.iri}>{item.label}</li>)}</ul>
    </section>
  );
}

function RelationshipSection({ title, relationships }: { title: string; relationships: WebRelationship[] }) {
  if (!relationships.length) return null;
  return (
    <section className="detail-section" aria-labelledby={`${title.toLowerCase().replaceAll(" ", "-")}-heading`}>
      <h3 id={`${title.toLowerCase().replaceAll(" ", "-")}-heading`}>{title}</h3>
      <ul className="relationship-list">
        {relationships.map((relationship) => <li key={`${relationship.sourceId}:${relationship.predicate.iri}:${relationship.value.value}`}>
          <strong>{relationship.predicate.label}</strong>
          <span>{formatValue(relationship.value)}</span>
        </li>)}
      </ul>
    </section>
  );
}

function formatValue(value: WebRdfValue) {
  return value.label ?? value.value;
}
