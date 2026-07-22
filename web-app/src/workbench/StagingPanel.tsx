import { useEffect, useRef, useState } from "react";
import { useStagedChanges, useStagingActions } from "../web/queries";
import type { WebDiffEntry, WebStagedEntry } from "../web/projectApi";

export default function StagingPanel({ projectId }: { projectId: string }) {
  const staged = useStagedChanges(projectId);
  const actions = useStagingActions(projectId);

  const entries = staged.data?.entries ?? [];
  const proposal = staged.data?.proposal;
  const approved = proposal?.status === "APPROVED" || proposal?.status === "APPLIED";
  const entryRevision = entries.map((entry) => `${entry.id}:${entry.status}`).join("|");
  const previewedRevision = useRef<string | null>(null);
  const failedPreviewRevision = useRef<string | null>(null);
  const previewMutation = useRef(actions.preview.mutate);
  const [notification, setNotification] = useState<string | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const decisionBusy = actions.approve.isPending || actions.reject.isPending || actions.apply.isPending || actions.accept.isPending;
  const preparingProposal = entries.length > 0 && actions.preview.isPending && !proposal;
  const busy = preparingProposal || actions.discard.isPending || decisionBusy;
  const actionError = stagingActionError(actions);

  useEffect(() => {
    previewMutation.current = actions.preview.mutate;
  }, [actions.preview.mutate]);

  useEffect(() => {
    if (!entryRevision) {
      previewedRevision.current = null;
      failedPreviewRevision.current = null;
      return;
    }
    if (proposal) {
      previewedRevision.current = entryRevision;
      failedPreviewRevision.current = null;
      return;
    }
    if (actions.preview.isPending || previewedRevision.current === entryRevision || failedPreviewRevision.current === entryRevision) return;
    prepareProposal(entryRevision);
  }, [actions.preview.isPending, entryRevision, proposal]);

  function prepareProposal(revision: string) {
    previewMutation.current(undefined, {
      onSuccess: (response) => {
        if (response.proposal) {
          previewedRevision.current = revision;
          failedPreviewRevision.current = null;
        } else {
          failedPreviewRevision.current = revision;
        }
      },
      onError: () => {
        failedPreviewRevision.current = revision;
      },
    });
  }

  function retryProposal() {
    failedPreviewRevision.current = null;
    previewedRevision.current = null;
    actions.preview.reset();
    prepareProposal(entryRevision);
  }

  useEffect(() => {
    if (!notification) return undefined;
    const timeout = window.setTimeout(() => setNotification(null), 5_000);
    return () => window.clearTimeout(timeout);
  }, [notification]);

  async function acceptProposal() {
    try {
      await actions.accept.mutateAsync();
      setNotification("Proposal accepted and applied. The project has been refreshed.");
    } catch {
      // The mutation state renders the server error without creating an unhandled rejection.
    }
  }

  async function rejectCurrentProposal() {
    try {
      await actions.reject.mutateAsync();
      setNotification("Proposal rejected. Its source files were not changed.");
    } catch {
      // The mutation state renders the server error without creating an unhandled rejection.
    }
  }

  async function applyRemediation(stagedChangeIds: string[]) {
    try {
      for (const stagedChangeId of [...new Set(stagedChangeIds)]) {
        await actions.discard.mutateAsync(stagedChangeId);
      }
      setNotification("The selected staged changes were removed. The proposal is being revalidated.");
    } catch {
      // The mutation state renders the server error without creating an unhandled rejection.
    }
  }

  async function removeEntry(entry: WebStagedEntry) {
    try {
      await actions.discard.mutateAsync(entry.id);
    } catch {
      // The mutation state renders the server error without creating an unhandled rejection.
    }
  }

  return (
    <section className="staging-panel" aria-labelledby="staging-heading" aria-busy={busy}>
      <div className="section-heading"><h2 id="staging-heading">Review queue</h2><span>{entries.length}</span></div>
      {actions.preview.isError ? <div className="workflow-error"><p role="alert">Could not prepare proposal. {actions.preview.error.message}</p><button className="button small" type="button" onClick={retryProposal} disabled={actions.preview.isPending}>Retry proposal</button></div> : null}
      {actionError ? <p className="workflow-error" role="alert">{actionError}</p> : null}
      {busy ? <p role="status">Updating staged changes...</p> : null}
      {!entries.length && !proposal ? <div className="empty-review-queue"><strong>No staged changes</strong><span>The shared review queue is empty.</span></div> : null}
      {entries.length && !proposal && busy ? <p role="status">Preparing proposal...</p> : null}
      {proposal && proposal.status !== "APPLIED" && proposal.status !== "REJECTED" ? <div className={`proposal-summary proposal-${proposal.status.toLowerCase()} ${approved ? "proposal-approved" : "proposal-pending"}`} aria-live="polite">
        <div className="proposal-summary-heading"><div><span className="overline">Proposal</span><h3>{displayStatus(proposal.status)}</h3></div><div className="proposal-summary-controls"><span className="proposal-status">{displayStatus(proposal.status)}</span>{proposal.status === "READYFORREVIEW" || proposal.status === "APPROVED" ? <button className="button primary small" type="button" onClick={() => void acceptProposal()} disabled={decisionBusy}>Accept</button> : null}{proposal.status !== "APPLIED" ? <button className="button danger small" type="button" onClick={() => void rejectCurrentProposal()} disabled={decisionBusy}>Reject</button> : null}</div></div>
        {proposal.message ? <p className="proposal-message">{proposal.message}</p> : null}
        <p className="proposal-message">This proposal contains {entries.length} staged edit{entries.length === 1 ? "" : "s"} and produces {proposal.diff.length} net graph change{proposal.diff.length === 1 ? "" : "s"}.</p>
        {proposal.status.includes("CONFLICT") || proposal.status.includes("FAILED") ? <p role="alert">This proposal needs review before it can continue.</p> : null}
        {(proposal.validationIssues ?? []).map((issue) => <section className="proposal-validation-issue" key={`${issue.code}:${issue.stagedChangeId}`} aria-labelledby={`proposal-issue-${issue.stagedChangeId}`}>
          <h4 id={`proposal-issue-${issue.stagedChangeId}`}>{displayStatus(issue.code)}</h4>
          <p role="alert">{labelFirstValidationMessage(issue.message, entries)}</p>
          <div className="button-row" aria-label="Resolve proposal validation error">
            {issue.remediations.map((remediation) => <button className="button small" key={`${issue.stagedChangeId}:${remediation.action}`} type="button" disabled={actions.discard.isPending || decisionBusy} onClick={() => void applyRemediation(remediation.stagedChangeIds)}>{remediation.label}</button>)}
          </div>
        </section>)}
        {proposal.validationMessages.filter((message) => !(proposal.validationIssues ?? []).some((issue) => issue.message === message)).map((message) => <p key={message} role="alert">{labelFirstValidationMessage(message, entries)}</p>)}
        {proposal.shaclImpact ? <section className="proposal-impact" aria-labelledby="shacl-impact-heading"><h4 id="shacl-impact-heading">SHACL finding impact</h4><div className="proposal-impact-row"><div className="impact-counts"><span><strong>{proposal.shaclImpact.newFindings.length}</strong> new</span><span><strong>{proposal.shaclImpact.worsenedFindings.length}</strong> worsened</span><span><strong>{proposal.shaclImpact.resolvedFindings.length}</strong> resolved</span></div><button className="button small" type="button" onClick={() => setDetailsOpen(true)} disabled={!entries.length}>View Details</button></div>{proposal.shaclImpact.newFindings.length ? <ul aria-label="New SHACL findings">{proposal.shaclImpact.newFindings.map((finding) => <li key={finding.resultId}><strong>{finding.severity}</strong> · {finding.message}</li>)}</ul> : null}</section> : <div className="proposal-summary-footer"><button className="button small" type="button" onClick={() => setDetailsOpen(true)} disabled={!entries.length}>View Details</button></div>}
      </div> : null}
      {detailsOpen && proposal ? <ProposalDetailsDialog
        entries={entries}
        diff={proposal.diff}
        onRemove={removeEntry}
        onAccept={acceptProposal}
        onReject={rejectCurrentProposal}
        onClose={() => setDetailsOpen(false)}
        canAccept={proposal.status === "READYFORREVIEW" || proposal.status === "APPROVED"}
        disabled={actions.discard.isPending || decisionBusy}
      /> : null}
      {notification ? <div className="workflow-toast" role="status" aria-live="polite">{notification}</div> : null}
    </section>
  );
}

function stagingActionError(actions: ReturnType<typeof useStagingActions>): string | null {
  if (actions.accept.isError) return `Could not accept and apply proposal. ${actions.accept.error.message}`;
  if (actions.approve.isError) return `Could not approve proposal. ${actions.approve.error.message}`;
  if (actions.reject.isError) return `Could not reject proposal. ${actions.reject.error.message}`;
  if (actions.apply.isError) return `Could not apply proposal. ${actions.apply.error.message}`;
  if (actions.discard.isError) return `Could not remove staged change. ${actions.discard.error.message}`;
  return null;
}

function ProposalDetailsDialog({ entries, diff, onRemove, onAccept, onReject, onClose, canAccept, disabled }: { entries: WebStagedEntry[]; diff: WebDiffEntry[]; onRemove: (entry: WebStagedEntry) => Promise<void>; onAccept: () => Promise<void>; onReject: () => Promise<void>; onClose: () => void; canAccept: boolean; disabled: boolean }) {
  return <div className="ai-modal-backdrop"><section className="ai-modal staging-details-modal" role="dialog" aria-modal="true" aria-labelledby="staging-details-heading">
    <header><div><span className="overline">Proposal</span><h2 id="staging-details-heading">View Details</h2></div><button className="icon-button" type="button" aria-label="Close proposal details" onClick={onClose}>×</button></header>
    <div className="staging-details-body">
      <p className="edit-dialog-description">Individual edits in this proposal. Labels are shown instead of IRIs wherever available.</p>
      <div className="staging-details-list">{entries.map((entry) => <article className="staging-detail-item" key={entry.id}>
        <div className="staging-detail-heading"><span className={`diff-kind diff-${entryOperation(entry)}`}>{entryOperationLabel(entry)}</span><strong>{stagedChangeTitle(entry)}</strong><span className="staged-status">{displayStatus(entry.status)}</span></div>
        <code>{stagedTriple(entry)}</code>
        <small>{entry.sourceId} · Staged by {entry.authorId}{entry.comment ? ` · ${entry.comment}` : ""}</small>
        <button className="button small" type="button" onClick={() => void onRemove(entry)} disabled={disabled}>Remove</button>
      </article>)}</div>
      {diff.length ? <section className="staging-details-diff" aria-labelledby="staging-net-changes-heading"><h4 id="staging-net-changes-heading">Net graph changes</h4><div className="staging-details-list" aria-label="Proposal semantic diff">{diff.map((entry, index) => <article className="staging-detail-item" key={`${entry.kind}-${entry.subject}-${index}`}><div className="staging-detail-heading"><span className={`diff-kind diff-${entry.kind.toLowerCase()}`}>{displayStatus(entry.kind)}</span><strong>{labelFirstDiff(entry, entries)}</strong></div></article>)}</div></section> : null}
    </div>
    <footer className="staging-details-footer"><div><button className="button danger" type="button" onClick={() => void onReject()} disabled={disabled}>Reject</button>{canAccept ? <button className="button primary" type="button" onClick={() => void onAccept()} disabled={disabled}>Accept</button> : null}</div><button className="button" type="button" onClick={onClose}>Close</button></footer>
  </section></div>;
}

function entryOperation(entry: WebStagedEntry): "added" | "removed" | "changed" {
  const operation = entry.normalizedValues.operation?.toLowerCase();
  if (operation === "add" || operation === "addition" || operation === "added") return "added";
  if (operation === "remove" || operation === "removal" || operation === "removed") return "removed";
  return entry.summary.toLowerCase().includes("remove") ? "removed" : "changed";
}

function entryOperationLabel(entry: WebStagedEntry): string {
  const operation = entryOperation(entry);
  return operation === "added" ? "Added" : operation === "removed" ? "Removed" : "Edit";
}

function stagedTriple(entry: WebStagedEntry): string {
  const values = entry.normalizedValues;
  if (values.tripleSummary) return values.tripleSummary;
  const labels = stagedLabelMap([entry]);
  const subject = values.subjectLabel || displayRdfTerm(values.subjectIri || values.resourceIri || values.shapeIri, labels) || values.resourceLabel || values.shapeLabel || "Subject";
  const predicate = values.predicateLabel || displayRdfTerm(values.predicateIri || values.propertyIri, labels) || values.propertyLabel || "Predicate";
  const object = values.objectLabel || displayRdfTerm(values.objectIri || values.objectValue || values.rangeClassIri, labels) || values.value || values.label || values.rangeClassLabel || "Object";
  return `${subject} — ${predicate} — ${object}`;
}

function stagedChangeTitle(entry: WebStagedEntry): string {
  return entry.summary.split(" · ").slice(1).join(" · ")
    || entry.normalizedValues.shapeLabel
    || entry.normalizedValues.label
    || entry.summary.replace(/^AI proposal:\s*/i, "").trim()
    || "Untitled change";
}

function displayStatus(value: string): string {
  const knownStatus = STATUS_LABELS[value.toUpperCase()];
  if (knownStatus) return knownStatus;
  return value
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/[-_]/g, " ")
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

function labelFirstDiff(entry: WebDiffEntry, stagedEntries: WebStagedEntry[]): string {
  const labels = stagedLabelMap(stagedEntries);
  const subject = displayRdfTerm(entry.subject, labels);
  const predicate = displayRdfTerm(entry.predicate, labels);
  const object = displayRdfTerm(entry.objectValue, labels);
  const verb = entry.kind.toLowerCase() === "removed" ? "Removed" : "Added";
  return [verb, subject, predicate, object].filter(Boolean).join(" · ");
}

function labelFirstValidationMessage(message: string, stagedEntries: WebStagedEntry[]): string {
  const labels = stagedLabelMap(stagedEntries);
  return message.replace(/https?:\/\/[^\s'"]+/g, (rawIri) => {
    const trailing = rawIri.match(/[),.;:!?]+$/)?.[0] ?? "";
    const iri = trailing ? rawIri.slice(0, -trailing.length) : rawIri;
    return `${displayRdfTerm(iri, labels)}${trailing}`;
  });
}

function stagedLabelMap(entries: WebStagedEntry[]): Map<string, string> {
  const labels = new Map<string, string>(KNOWN_RDF_LABELS);
  entries.forEach((entry) => {
    Object.entries(entry.normalizedValues).forEach(([key, iri]) => {
      if (!key.endsWith("Iri") || !looksLikeIri(iri)) return;
      const stem = key.slice(0, -3);
      const label = entry.normalizedValues[`${stem}Label`]
        ?? (stem === "class" || stem === "property" || stem === "individual" ? entry.normalizedValues.label : undefined);
      if (label) labels.set(iri, label);
    });
  });
  return labels;
}

function displayRdfTerm(value: string | null, labels: Map<string, string>): string {
  if (!value) return "";
  const known = labels.get(value);
  if (known) return known;
  if (!looksLikeIri(value)) return literalLabel(value);
  const localName = value.split(/[\/#]/).filter(Boolean).at(-1) ?? value;
  return decodeURIComponent(localName).replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[-_]/g, " ");
}

function literalLabel(value: string): string {
  const lexical = value.match(/^"([\s\S]*)"(?:\^\^|@|$)/)?.[1];
  return lexical ? `“${lexical}”` : value;
}

function looksLikeIri(value: string): boolean {
  return /^https?:\/\//.test(value);
}

const KNOWN_RDF_LABELS: ReadonlyArray<readonly [string, string]> = [
  ["http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "type"],
  ["http://www.w3.org/2000/01/rdf-schema#label", "label"],
  ["http://www.w3.org/2000/01/rdf-schema#subClassOf", "superclass"],
  ["http://www.w3.org/2000/01/rdf-schema#domain", "domain"],
  ["http://www.w3.org/2000/01/rdf-schema#range", "range"],
  ["http://www.w3.org/2002/07/owl#Class", "Class"],
  ["http://www.w3.org/2002/07/owl#ObjectProperty", "Object property"],
  ["http://www.w3.org/2002/07/owl#DatatypeProperty", "Datatype property"],
  ["http://www.w3.org/2002/07/owl#NamedIndividual", "Individual"],
];

const STATUS_LABELS: Record<string, string> = {
  READYFORREVIEW: "Ready for review",
  READY_FOR_REVIEW: "Ready for review",
  STAGED: "Staged",
  PREVIEWED: "Previewed",
  APPROVED: "Approved",
  APPLIED: "Applied",
  REJECTED: "Rejected",
  ROLLEDBACK: "Rolled back",
  APPLYFAILED: "Apply failed",
  VERIFICATIONFAILED: "Verification failed",
};
