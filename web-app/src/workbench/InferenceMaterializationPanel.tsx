import { useEffect, useMemo, useRef, useState } from "react";
import type { WebInferenceMaterializationCandidate, WebReasoningFact, WebSemanticJobState } from "../web/projectApi";
import { useInferenceMaterialization } from "../web/queries";

interface InferenceMaterializationPanelProps {
  projectId: string;
  jobId: string;
  jobStatus: WebSemanticJobState;
  facts: WebReasoningFact[];
  candidates: WebInferenceMaterializationCandidate[];
  truncated: boolean;
  onOpenChanges: () => void;
}

export default function InferenceMaterializationPanel({
  projectId,
  jobId,
  jobStatus,
  facts,
  candidates,
  truncated,
  onOpenChanges,
}: InferenceMaterializationPanelProps) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [sourceChoices, setSourceChoices] = useState<Record<string, string>>({});
  const idempotencyKey = useRef<string | null>(null);
  const materialize = useInferenceMaterialization(projectId, jobId);
  const asserted = facts.filter((fact) => fact.origin === "Asserted");
  const selectable = useMemo(
    () => candidates.filter((candidate) => candidate.stageability === "Stageable" || candidate.stageability === "AmbiguousSource"),
    [candidates],
  );
  const selectionSignature = [...selected].sort().map((factId) => `${factId}:${sourceChoices[factId] ?? ""}`).join("|");

  useEffect(() => {
    setSelected(new Set());
    setSourceChoices({});
    idempotencyKey.current = null;
  }, [projectId, jobId, jobStatus]);

  useEffect(() => {
    idempotencyKey.current = null;
  }, [selectionSignature]);

  const missingSourceChoice = selectable.some(
    (candidate) => selected.has(candidate.factId) &&
      candidate.stageability === "AmbiguousSource" &&
      !sourceChoices[candidate.factId],
  );
  const canSubmit = selected.size > 0 && !missingSourceChoice && jobStatus === "Completed" && !materialize.isPending;

  function toggle(factId: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(factId)) next.delete(factId); else next.add(factId);
      return next;
    });
  }

  function selectAllVisible() {
    setSelected(new Set(selectable.slice(0, 100).map((candidate) => candidate.factId)));
  }

  function clearSelection() {
    setSelected(new Set());
    setSourceChoices({});
  }

  function stageSelected() {
    if (!canSubmit) return;
    idempotencyKey.current ??= createIdempotencyKey();
    const selections = candidates
      .filter((candidate) => selected.has(candidate.factId))
      .map((candidate) => ({
        factId: candidate.factId,
        ...(sourceChoices[candidate.factId] ? { targetSourceId: sourceChoices[candidate.factId] } : {}),
      }));
    materialize.mutate(
      { selections, idempotencyKey: idempotencyKey.current },
      {
        onSuccess: () => {
          clearSelection();
          idempotencyKey.current = null;
          onOpenChanges();
        },
      },
    );
  }

  return <section className="inference-materialization" aria-labelledby="reasoning-facts-heading">
    <div className="section-heading">
      <div>
        <h3 id="reasoning-facts-heading">Reasoning facts</h3>
        <p className="muted">Review inferred facts before staging any as explicit assertions.</p>
      </div>
      <span className="selection-count" aria-live="polite">{selected.size} selected</span>
    </div>

    <section aria-labelledby="asserted-facts-heading">
      <h4 id="asserted-facts-heading">Asserted facts <span className="fact-origin">Asserted</span></h4>
      {asserted.length ? <ul className="asserted-fact-list">
        {asserted.map((fact, index) => <li key={`${fact.kind}-${fact.subject}-${fact.objectValue}-${index}`}>
          <span>{shorten(fact.subject)}</span>
          <span>{shorten(fact.predicate ?? fact.kind)}</span>
          <span>{shorten(fact.objectValue)}</span>
        </li>)}
      </ul> : <p className="muted">No asserted facts are included in this bounded result.</p>}
    </section>

    <section aria-labelledby="inferred-facts-heading">
      <div className="inference-list-heading">
        <h4 id="inferred-facts-heading">Inferred facts <span className="fact-origin inferred">Inferred</span></h4>
        <div className="inference-selection-actions">
          <button type="button" className="button small" onClick={selectAllVisible} disabled={!selectable.length}>Select all visible</button>
          <button type="button" className="button small" onClick={clearSelection} disabled={!selected.size}>Clear selection</button>
        </div>
      </div>
      {candidates.length ? <div className="inference-fact-table" role="table" aria-label="Inferred materialization candidates">
        <div className="inference-fact-row header" role="row">
          <span role="columnheader">Select</span><span role="columnheader">Fact</span><span role="columnheader">Type</span>
          <span role="columnheader">Target source</span><span role="columnheader">Status</span>
        </div>
        {candidates.map((candidate) => {
          const enabled = candidate.stageability === "Stageable" || candidate.stageability === "AmbiguousSource";
          return <div className={`inference-fact-row ${enabled ? "" : "disabled"}`} role="row" key={candidate.factId}>
            <span role="cell">
              <input
                type="checkbox"
                aria-label={`Select ${candidate.subjectLabel} ${candidate.predicateLabel} ${candidate.objectLabel}`}
                checked={selected.has(candidate.factId)}
                disabled={!enabled}
                onChange={() => toggle(candidate.factId)}
              />
            </span>
            <span role="cell" className="inference-fact-statement">
              <strong>{candidate.subjectLabel}</strong>
              <span>{candidate.predicateLabel}</span>
              <strong>{candidate.objectLabel}</strong>
              {candidate.importDependence === "Imported" ? <small>Depends on imported knowledge</small> : null}
            </span>
            <span role="cell">{formatKind(candidate.kind)}</span>
            <span role="cell">
              {candidate.sourceCandidates.length > 1 ? <label>
                <span className="sr-only">Target source for {candidate.subjectLabel}</span>
                <select
                  aria-label={`Target source for ${candidate.subjectLabel}`}
                  value={sourceChoices[candidate.factId] ?? ""}
                  onChange={(event) => setSourceChoices((current) => ({ ...current, [candidate.factId]: event.target.value }))}
                >
                  <option value="">Choose source</option>
                  {candidate.sourceCandidates.map((source) => <option key={source.sourceId} value={source.sourceId}>{source.sourceId}</option>)}
                </select>
              </label> : <span>{candidate.selectedSourceId ?? "Not available"}</span>}
            </span>
            <span role="cell">
              <strong>{formatStageability(candidate.stageability)}</strong>
              <small>{candidate.reason}</small>
              {candidate.existingStagedChangeId ? <small>Existing item: {candidate.existingStagedChangeId}</small> : null}
            </span>
          </div>;
        })}
      </div> : <p className="muted">No supported inferred facts are available in this result.</p>}
    </section>

    {truncated ? <p role="status">Only the first 100 loaded facts are shown and selectable.</p> : null}
    {missingSourceChoice ? <p role="status">Choose a target source for every ambiguous selected fact.</p> : null}
    {materialize.isPending ? <p role="status" aria-live="polite">Reloading the applied graph and rechecking reasoning before staging…</p> : null}
    {materialize.isError ? <p role="alert">Could not stage the selected inferred facts. {materialize.error.message}</p> : null}
    <button type="button" className="button primary" disabled={!canSubmit} onClick={stageSelected}>
      Stage as asserted
    </button>
  </section>;
}

function formatKind(kind: WebInferenceMaterializationCandidate["kind"]): string {
  if (kind === "SubclassRelationship") return "Subclass";
  if (kind === "IndividualType") return "Individual type";
  return "Object property";
}

function formatStageability(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2");
}

function shorten(value: string): string {
  return value.slice(Math.max(value.lastIndexOf("#"), value.lastIndexOf("/")) + 1);
}

function createIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return `materialize-${crypto.randomUUID()}`;
  return `materialize-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
