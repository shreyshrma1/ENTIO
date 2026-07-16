import { FormEvent, useState } from "react";
import { useStagedChanges, useStagingActions } from "../web/queries";

export default function StagingPanel({ projectId, sourceId }: { projectId: string; sourceId: string }) {
  const staged = useStagedChanges(projectId);
  const actions = useStagingActions(projectId);
  const [classIri, setClassIri] = useState("");
  const [label, setLabel] = useState("");

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    actions.stage.mutate({
      sourceId,
      editType: "create-class",
      classIri: classIri.trim(),
      label: label.trim() || undefined,
      idempotencyKey: `web-${Date.now()}`,
    });
  }

  const entries = staged.data?.entries ?? [];
  const proposal = staged.data?.proposal;
  const busy = actions.stage.isPending || actions.preview.isPending || actions.approve.isPending || actions.reject.isPending || actions.apply.isPending;

  return (
    <section className="staging-panel" aria-labelledby="staging-heading" aria-busy={busy}>
      <div className="section-heading"><h2 id="staging-heading">Staged changes</h2><span>{entries.length}</span></div>
      <form className="staging-form" onSubmit={submit}>
        <label htmlFor="new-class-iri">Class IRI</label>
        <input id="new-class-iri" value={classIri} onChange={(event) => setClassIri(event.target.value)} placeholder="https://example.com/entio/simple#Account" required />
        <label htmlFor="new-class-label">Label</label>
        <input id="new-class-label" value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Account" />
        <button type="submit" disabled={busy || !classIri.trim()}>Stage class</button>
      </form>
      {actions.stage.isError ? <p role="alert">Could not stage change. {actions.stage.error.message}</p> : null}
      {actions.preview.isError ? <p role="alert">Could not prepare proposal. {actions.preview.error.message}</p> : null}
      {busy ? <p role="status">Updating staged changes...</p> : null}
      {entries.length ? <ul className="staged-list">{entries.map((entry) => <li key={entry.id}>
        <div><strong>{entry.summary}</strong><small>{entry.status} · {entry.authorId}{entry.comment ? ` · ${entry.comment}` : ""}</small></div>
        <button type="button" onClick={() => actions.discard.mutate(entry.id)} disabled={busy}>Remove</button>
      </li>)}</ul> : <p className="muted">Drafts remain private until staged here.</p>}
      {entries.length ? <div className="proposal-actions">
        {!proposal || proposal.status === "REJECTED" ? <button type="button" onClick={() => actions.preview.mutate()} disabled={busy}>Preview proposal</button> : null}
        {proposal?.status === "READYFORREVIEW" ? <button type="button" onClick={() => actions.approve.mutate()} disabled={busy}>Approve</button> : null}
        {proposal && proposal.status !== "APPLIED" ? <button type="button" onClick={() => actions.reject.mutate()} disabled={busy}>Reject</button> : null}
        {proposal?.status === "APPROVED" ? <button type="button" onClick={() => actions.apply.mutate()} disabled={busy}>Apply</button> : null}
      </div> : null}
      {proposal ? <div className={`proposal-summary proposal-${proposal.status.toLowerCase()}`} aria-live="polite"><strong>Proposal {proposal.status.toLowerCase()}</strong>{proposal.message ? <p>{proposal.message}</p> : null}{proposal.status.includes("CONFLICT") || proposal.status.includes("FAILED") ? <p role="alert">This proposal needs review before it can continue.</p> : null}{proposal.validationMessages.map((message) => <p key={message} role="alert">{message}</p>)}{proposal.diff.length ? <ul aria-label="Proposal semantic diff">{proposal.diff.map((entry) => <li key={`${entry.kind}-${entry.description}`}>{entry.description}</li>)}</ul> : null}</div> : null}
    </section>
  );
}
