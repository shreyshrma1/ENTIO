import { useEffect, useState } from "react";
import { useSemanticJob, useSemanticJobActions, useStagedChanges } from "../web/queries";
import type { WebSemanticJobStatus } from "../web/projectApi";

interface SemanticJobPanelProps {
  projectId: string;
  initialJobId?: string | null;
  onJobSubmitted?: (kind: "reasoning" | "shacl", status: WebSemanticJobStatus) => void;
}

export default function SemanticJobPanel({ projectId, initialJobId = null, onJobSubmitted }: SemanticJobPanelProps) {
  const [scope, setScope] = useState<"applied" | "proposal">("applied");
  const [jobId, setJobId] = useState<string | null>(initialJobId);
  const job = useSemanticJob(projectId, jobId);
  const actions = useSemanticJobActions(projectId);
  const staged = useStagedChanges(projectId);
  const hasProposal = Boolean(staged.data?.proposal);
  const busy = actions.submit.isPending;

  useEffect(() => { setJobId(initialJobId); }, [initialJobId]);

  function start(kind: "reasoning" | "shacl") {
    actions.submit.mutate(
      { kind, scope, ...(kind === "shacl" ? { mode: "asserted-only" as const } : {}) },
      {
        onSuccess: (status) => {
          setJobId(status.id);
          onJobSubmitted?.(kind, status);
        },
      },
    );
  }

  const status = job.data;
  const terminal = status && ["Completed", "Failed", "Cancelled", "Incomplete", "Stale"].includes(status.status);

  return (
    <section className="semantic-job-panel" aria-labelledby="semantic-jobs-heading" aria-busy={busy || job.isPending}>
      <div className="section-heading">
        <h2 id="semantic-jobs-heading">Reasoning and SHACL</h2>
        {status ? <span className={`job-state job-${status.status.toLowerCase()}`}>{status.status}</span> : null}
      </div>
      <div className="semantic-job-controls">
        <label htmlFor="semantic-job-scope">Graph scope</label>
        <select id="semantic-job-scope" value={scope} onChange={(event) => setScope(event.target.value as "applied" | "proposal")}>
          <option value="applied">Applied graph</option>
          <option value="proposal" disabled={!hasProposal}>Current proposal</option>
        </select>
        <button className="button primary" type="button" onClick={() => start("reasoning")} disabled={busy || (scope === "proposal" && !hasProposal)}>Refresh reasoning</button>
        <button className="button" type="button" onClick={() => start("shacl")} disabled={busy || (scope === "proposal" && !hasProposal)}>Validate SHACL</button>
      </div>
      {actions.submit.isError ? <p role="alert">Could not start semantic work. {actions.submit.error.message}</p> : null}
      {actions.cancel.isError ? <p role="alert">Could not cancel semantic work. {actions.cancel.error.message}</p> : null}
      {jobId && job.isPending ? <p role="status">Loading semantic job...</p> : null}
      {job.isError ? <p role="alert">Could not load semantic job. {job.error.message}</p> : null}
      {status ? (
        <div className="semantic-job-status" aria-live="polite">
          <p><strong>{status.kind} · {status.scope}</strong> · {status.phase}</p>
          <p>{status.message}</p>
          <small>Graph fingerprint: {status.graphFingerprint.slice(0, 12)}…</small>
          {status.proposalFingerprint ? <small>Proposal fingerprint: {status.proposalFingerprint.slice(0, 12)}…</small> : null}
          {Object.keys(status.resultSummary).length ? <SemanticResultSummary status={status} /> : null}
          {status.error ? <p role="alert">{status.error}</p> : null}
          {status.status === "Stale" ? <p role="alert">This result is stale because the graph or staged proposal changed.</p> : null}
          {!terminal && status.status !== "Queued" ? <button className="button small" type="button" onClick={() => actions.cancel.mutate(status.id)} disabled={actions.cancel.isPending}>Cancel semantic job</button> : null}
        </div>
      ) : <p className="muted">Run deterministic reasoning or SHACL validation against the applied graph or current proposal.</p>}
    </section>
  );
}

function SemanticResultSummary({ status }: { status: WebSemanticJobStatus }) {
  return <section className="semantic-result-summary" aria-label={`${status.kind} result summary`}>
    <h3>{status.kind === "Shacl" ? "Validation result" : "Reasoning result"}</h3>
    <dl>{Object.entries(status.resultSummary).map(([key, value]) => <div key={key}><dt>{formatSummaryKey(key)}</dt><dd>{formatSummaryValue(value)}</dd></div>)}</dl>
  </section>;
}

function formatSummaryKey(key: string): string {
  return key.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^./, (character) => character.toUpperCase());
}

function formatSummaryValue(value: unknown): React.ReactNode {
  if (Array.isArray(value)) {
    return value.length ? <ul>{value.map((item, index) => <li key={`${String(item)}-${index}`}>{typeof item === "string" ? item : JSON.stringify(item)}</li>)}</ul> : "None";
  }
  if (value && typeof value === "object") return <code>{JSON.stringify(value)}</code>;
  return String(value ?? "None");
}
