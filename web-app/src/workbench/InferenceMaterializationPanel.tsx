import { useEffect, useState, type FormEvent } from "react";
import type { WebReasoningFact, WebSemanticJobState } from "../web/projectApi";
import { useSemanticJobDetails } from "../web/queries";

interface ReasoningFactsPanelProps {
  projectId: string;
  jobId: string;
  jobStatus: WebSemanticJobState;
}

export default function ReasoningFactsPanel({ projectId, jobId, jobStatus }: ReasoningFactsPanelProps) {
  const [openOrigin, setOpenOrigin] = useState<"Asserted" | "Inferred" | null>(null);

  return <section className="reasoning-fact-browsers" aria-labelledby="reasoning-facts-heading">
    <div className="section-heading">
      <div>
        <h3 id="reasoning-facts-heading">Reasoning facts</h3>
        <p className="muted">Browse the asserted input and the relationships inferred from it.</p>
      </div>
    </div>
    <div className="reasoning-fact-browser-buttons">
      <button type="button" className="reasoning-fact-browser-button" onClick={() => setOpenOrigin("Asserted")} disabled={jobStatus !== "Completed"}>
        <strong>Asserted facts</strong>
        <span>Browse relationships present in the applied graph</span>
      </button>
      <button type="button" className="reasoning-fact-browser-button" onClick={() => setOpenOrigin("Inferred")} disabled={jobStatus !== "Completed"}>
        <strong>Inferred facts</strong>
        <span>Browse relationships produced by reasoning</span>
      </button>
    </div>
    {openOrigin ? <ReasoningFactDialog projectId={projectId} jobId={jobId} origin={openOrigin} onClose={() => setOpenOrigin(null)} /> : null}
  </section>;
}

function ReasoningFactDialog({ projectId, jobId, origin, onClose }: {
  projectId: string;
  jobId: string;
  origin: "Asserted" | "Inferred";
  onClose: () => void;
}) {
  const [input, setInput] = useState("");
  const [query, setQuery] = useState("");
  const [offset, setOffset] = useState(0);
  const [facts, setFacts] = useState<WebReasoningFact[]>([]);
  const page = useSemanticJobDetails(projectId, jobId, { factOrigin: origin, factOffset: offset, factQuery: query, limit: 50 });

  useEffect(() => {
    if (!page.data) return;
    setFacts((current) => {
      const combined = offset === 0 ? page.data.facts : [...current, ...page.data.facts];
      return [...new Map(combined.map((fact) => [factKey(fact), fact])).values()];
    });
  }, [offset, page.data]);

  function search(event: FormEvent) {
    event.preventDefault();
    setFacts([]);
    setOffset(0);
    setQuery(input.trim());
  }

  return <div className="reasoning-fact-dialog-backdrop">
    <section className="reasoning-fact-dialog" role="dialog" aria-modal="true" aria-labelledby="reasoning-fact-dialog-title">
      <header>
        <div>
          <p className="eyebrow">{origin}</p>
          <h3 id="reasoning-fact-dialog-title">{origin} facts</h3>
        </div>
        <button type="button" className="icon-button" aria-label={`Close ${origin.toLowerCase()} facts`} onClick={onClose}>×</button>
      </header>
      <form className="reasoning-fact-search" role="search" onSubmit={search}>
        <label htmlFor={`reasoning-${origin.toLowerCase()}-search`}>Search relationships</label>
        <div>
          <input id={`reasoning-${origin.toLowerCase()}-search`} value={input} onChange={(event) => setInput(event.target.value)} placeholder="Search subject, relationship, or object" />
          <button type="submit" className="button">Search</button>
        </div>
      </form>
      <p className="reasoning-fact-count">{page.data ? `${facts.length} of ${page.data.totalFactCount}` : "Loading facts…"}</p>
      <div className="reasoning-fact-scroll">
        {page.isError ? <p role="alert">Could not load reasoning facts. {page.error.message}</p> : null}
        {!facts.length && page.isPending ? <p role="status">Loading reasoning facts…</p> : null}
        {!facts.length && page.data && !page.data.totalFactCount ? <p className="muted">No facts match this search.</p> : null}
        {facts.length ? <ul className="reasoning-fact-list">{facts.map((fact) => <li key={factKey(fact)}>
          <strong>{shorten(fact.subject)}</strong>
          <span>{shorten(fact.predicate ?? fact.kind)}</span>
          <strong>{shorten(fact.objectValue)}</strong>
          <small>{formatKind(fact.kind)}</small>
        </li>)}</ul> : null}
      </div>
      <footer>
        {page.data?.nextFactOffset != null ? <button type="button" className="button" disabled={page.isFetching} onClick={() => setOffset(page.data!.nextFactOffset!)}>{page.isFetching ? "Loading…" : "Load more"}</button> : null}
        <button type="button" className="button primary" onClick={onClose}>Done</button>
      </footer>
    </section>
  </div>;
}

function factKey(fact: WebReasoningFact): string {
  return [fact.origin, fact.kind, fact.subject, fact.predicate, fact.objectValue].join("|");
}

function formatKind(kind: string): string {
  if (kind === "class-relationship") return "Class relationship";
  if (kind === "individual-type") return "Individual type";
  if (kind === "property-relationship") return "Object-property relationship";
  return kind;
}

function shorten(value: string): string {
  try {
    return decodeURIComponent(value.slice(Math.max(value.lastIndexOf("#"), value.lastIndexOf("/")) + 1));
  } catch {
    return value;
  }
}
