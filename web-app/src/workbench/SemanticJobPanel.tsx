import { useEffect, useRef, useState } from "react";
import { useSemanticJob, useSemanticJobActions, useSemanticJobDetails, useStagedChanges } from "../web/queries";
import type { WebSemanticJobStatus } from "../web/projectApi";
import InferenceMaterializationPanel from "./InferenceMaterializationPanel";

interface SemanticJobPanelProps {
  projectId: string;
  initialJobId?: string | null;
  onJobSubmitted?: (kind: "reasoning" | "shacl", status: WebSemanticJobStatus) => void;
  showReasoning?: boolean;
  showShacl?: boolean;
  showHeading?: boolean;
  autoStartReasoning?: boolean;
  headingId?: string;
  onOpenChanges?: () => void;
}

export default function SemanticJobPanel({ projectId, initialJobId = null, onJobSubmitted, showReasoning = true, showShacl = true, showHeading = true, autoStartReasoning = true, headingId = "semantic-jobs-heading", onOpenChanges = () => {} }: SemanticJobPanelProps) {
  const [scope, setScope] = useState<"applied" | "proposal">("applied");
  const [jobId, setJobId] = useState<string | null>(initialJobId);
  const job = useSemanticJob(projectId, jobId);
  const details = useSemanticJobDetails(projectId, jobId);
  const actions = useSemanticJobActions(projectId);
  const staged = useStagedChanges(projectId);
  const hasProposal = Boolean(staged.data?.proposal);
  const busy = actions.submit.isPending;
  const autoStarted = useRef(false);

  useEffect(() => { setJobId(initialJobId); }, [initialJobId]);

  useEffect(() => {
    if (!autoStartReasoning || !showReasoning || initialJobId || autoStarted.current) return;
    autoStarted.current = true;
    start("reasoning");
  }, [autoStartReasoning, initialJobId, showReasoning, projectId]);

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
    <section className="semantic-job-panel" aria-labelledby={showHeading ? headingId : undefined} aria-label={showHeading ? undefined : "SHACL validation"} aria-busy={busy || job.isPending}>
      {showHeading ? <div className="section-heading">
        <h2 id={headingId}>{showReasoning ? "Reasoning" : "SHACL validation"}</h2>
        {status ? <span className={`job-state job-${status.status.toLowerCase()}`}>{status.status}</span> : null}
      </div> : null}
      <div className="semantic-job-controls">
        <label htmlFor="semantic-job-scope">Graph scope
          <select id="semantic-job-scope" value={scope} onChange={(event) => setScope(event.target.value as "applied" | "proposal")}>
            <option value="applied">Applied graph</option>
            <option value="proposal" disabled={!hasProposal}>Current proposal</option>
          </select>
        </label>
        {showReasoning ? <button className="button primary" type="button" onClick={() => start("reasoning")} disabled={scope === "proposal" && !hasProposal}>Refresh reasoning</button> : null}
        {showShacl ? <button className={`button ${showReasoning ? "" : "primary"}`} type="button" onClick={() => start("shacl")} disabled={scope === "proposal" && !hasProposal}>Validate SHACL</button> : null}
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
      ) : showReasoning && !showShacl ? null : <p className="muted">Choose a graph, then run validation.</p>}
      {showReasoning && status?.kind === "Reasoning" && status.scope === "Applied" && status.status === "Completed" && details.data ? (
        <InferenceMaterializationPanel
          projectId={projectId}
          jobId={status.id}
          jobStatus={details.data.job.status}
          facts={details.data.facts}
          candidates={details.data.materializationCandidates}
          truncated={details.data.truncated}
          onOpenChanges={onOpenChanges}
        />
      ) : null}
      {showReasoning && status?.status === "Completed" && details.isError ? <p role="alert">Could not load bounded reasoning facts. {details.error.message}</p> : null}
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
