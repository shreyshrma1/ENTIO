import { FormEvent, useEffect, useMemo, useState } from "react";
import { useProjectSummary, useStagedChanges, useStagingActions } from "../web/queries";
import {
  buildStageChangeRequest,
  STAGING_EDIT_DEFINITIONS,
  stagingEditDefinition,
  type StagingFormValues,
  type WebStagingEditType,
} from "./stagingEditTypes";

export default function StagingPanel({ projectId, sourceId }: { projectId: string; sourceId: string }) {
  const staged = useStagedChanges(projectId);
  const summary = useProjectSummary(projectId);
  const actions = useStagingActions(projectId);
  const [editType, setEditType] = useState<WebStagingEditType>("create-class");
  const [values, setValues] = useState<StagingFormValues>({});
  const [selectedSourceId, setSelectedSourceId] = useState(sourceId);
  const [formError, setFormError] = useState<string | null>(null);
  const definition = useMemo(() => stagingEditDefinition(editType), [editType]);
  const shaclEdit = editType.startsWith("shacl-");
  const targetSources = (summary.data?.sources ?? []).filter((source) =>
    shaclEdit ? source.roles.map((role) => role.toLowerCase()).includes("shapes") : !source.roles.map((role) => role.toLowerCase()).includes("shapes"),
  );
  const targetSourceId = targetSources.some((source) => source.id === selectedSourceId) ? selectedSourceId : targetSources[0]?.id ?? sourceId;
  useEffect(() => {
    if (!targetSources.some((source) => source.id === selectedSourceId)) setSelectedSourceId(targetSources[0]?.id ?? sourceId);
  }, [selectedSourceId, sourceId, targetSources]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      setFormError(null);
      const request = buildStageChangeRequest(targetSourceId, editType, values, `web-${Date.now()}`);
      actions.stage.mutate(request, { onSuccess: () => setValues({}) });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "The change could not be staged.");
    }
  }

  function changeEditType(next: WebStagingEditType) {
    setEditType(next);
    setValues({});
    setFormError(null);
  }

  const entries = staged.data?.entries ?? [];
  const proposal = staged.data?.proposal;
  const busy = actions.stage.isPending || actions.preview.isPending || actions.approve.isPending || actions.reject.isPending || actions.apply.isPending;

  return (
    <section className="staging-panel" aria-labelledby="staging-heading" aria-busy={busy}>
      <div className="section-heading"><h2 id="staging-heading">Staged changes</h2><span>{entries.length}</span></div>
      <form className="staging-form" onSubmit={submit}>
        <label htmlFor="staging-edit-type">Change type
          <select id="staging-edit-type" value={editType} onChange={(event) => changeEditType(event.target.value as WebStagingEditType)}>
            {STAGING_EDIT_DEFINITIONS.map((item) => <option key={item.type} value={item.type}>{item.label}</option>)}
          </select>
        </label>
        <label htmlFor="staging-source">Target source
          <select id="staging-source" value={targetSourceId} onChange={(event) => setSelectedSourceId(event.target.value)} disabled={targetSources.length <= 1}>
            {targetSources.map((source) => <option key={source.id} value={source.id}>{source.id} · {source.path}</option>)}
          </select>
        </label>
        <p className="staging-edit-description" id="staging-edit-description">{definition.description}</p>
        <div className="staging-form-fields" aria-describedby="staging-edit-description">
          {definition.fields.map((field) => <label key={field.key} htmlFor={`staging-${field.key}`}>{field.label}
            <input
              id={`staging-${field.key}`}
              value={values[field.key] ?? ""}
              onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))}
              placeholder={field.placeholder}
              required={field.required}
              spellCheck
            />
          </label>)}
        </div>
        <button className="button primary" type="submit" disabled={busy}>Stage change</button>
      </form>
      {formError ? <p role="alert">{formError}</p> : null}
      {actions.stage.isError ? <p role="alert">Could not stage change. {actions.stage.error.message}</p> : null}
      {actions.preview.isError ? <p role="alert">Could not prepare proposal. {actions.preview.error.message}</p> : null}
      {busy ? <p role="status">Updating staged changes...</p> : null}
      {entries.length ? <ul className="staged-list">{entries.map((entry) => <li key={entry.id}>
        <div><strong>{entry.summary}</strong><small>{entry.status} · {entry.authorId}{entry.comment ? ` · ${entry.comment}` : ""}</small></div>
        <button className="button small" type="button" onClick={() => actions.discard.mutate(entry.id)} disabled={busy}>Remove</button>
      </li>)}</ul> : <p className="muted">Drafts remain private until staged here.</p>}
      {entries.length ? <div className="proposal-actions">
        {!proposal || proposal.status === "REJECTED" ? <button className="button primary" type="button" onClick={() => actions.preview.mutate()} disabled={busy}>Preview proposal</button> : null}
        {proposal?.status === "READYFORREVIEW" ? <button className="button primary" type="button" onClick={() => actions.approve.mutate()} disabled={busy}>Approve</button> : null}
        {proposal && proposal.status !== "APPLIED" ? <button className="button danger" type="button" onClick={() => actions.reject.mutate()} disabled={busy}>Reject</button> : null}
        {proposal?.status === "APPROVED" ? <button className="button primary" type="button" onClick={() => actions.apply.mutate()} disabled={busy}>Apply</button> : null}
      </div> : null}
      {proposal ? <div className={`proposal-summary proposal-${proposal.status.toLowerCase()}`} aria-live="polite"><strong>Proposal {proposal.status.toLowerCase()}</strong>{proposal.message ? <p>{proposal.message}</p> : null}{proposal.status.includes("CONFLICT") || proposal.status.includes("FAILED") ? <p role="alert">This proposal needs review before it can continue.</p> : null}{proposal.validationMessages.map((message) => <p key={message} role="alert">{message}</p>)}{proposal.shaclImpact ? <div><h3>SHACL finding impact</h3><p>{proposal.shaclImpact.newFindings.length} new · {proposal.shaclImpact.worsenedFindings.length} worsened · {proposal.shaclImpact.resolvedFindings.length} resolved</p>{proposal.shaclImpact.newFindings.length ? <ul aria-label="New SHACL findings">{proposal.shaclImpact.newFindings.map((finding) => <li key={finding.resultId}><strong>{finding.severity}</strong> · {finding.message}</li>)}</ul> : null}</div> : null}{proposal.diff.length ? <ul aria-label="Proposal semantic diff">{proposal.diff.map((entry) => <li key={`${entry.kind}-${entry.description}`}>{entry.description}</li>)}</ul> : null}</div> : null}
    </section>
  );
}
