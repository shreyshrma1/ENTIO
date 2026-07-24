import { useEffect, useRef, useState, type RefObject } from "react";
import {
  useCancelDocumentTask,
  useDeleteDocumentTask,
  useDocumentEvidence,
  useDocumentIngestionTasks,
  useDocumentReview,
  useDocumentReviewDecision,
  useUploadDocuments,
} from "../../web/queries";
import type {
  WebDocumentEvidenceView,
  WebDocumentReviewDecision,
  WebDocumentReviewRecommendation,
} from "../../web/projectApi";

export default function DocumentIngestionWorkspace({ projectId }: { projectId: string }) {
  const tasks = useDocumentIngestionTasks(projectId);
  const upload = useUploadDocuments(projectId);
  const cancel = useCancelDocumentTask(projectId);
  const remove = useDeleteDocumentTask(projectId);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [authorityStatus, setAuthorityStatus] = useState("Supporting");
  const [businessArea, setBusinessArea] = useState("");
  const [jurisdiction, setJurisdiction] = useState("");
  const taskItems = tasks.data?.items ?? [];
  const selectedTask = taskItems.find((task) => task.taskId === selectedTaskId) ?? null;

  useEffect(() => {
    if (!selectedTaskId && taskItems.length) setSelectedTaskId(taskItems[0].taskId);
  }, [selectedTaskId, taskItems]);

  return (
    <div className="document-workspace">
      <header className="document-workspace-header">
        <div><span className="overline">Controlled ingestion</span><h1>Documents</h1></div>
        <p>Upload trusted source material, then review evidence-linked recommendations before any draft is created.</p>
      </header>

      <form className="document-upload-card" onSubmit={(event) => {
        event.preventDefault();
        if (!files.length) return;
        upload.mutate({ files, authorityStatus, businessArea, jurisdiction }, {
          onSuccess: (task) => { setFiles([]); setSelectedTaskId(task.taskId); },
        });
      }}>
        <label>Documents
          <input
            type="file"
            accept=".pdf,.docx,.txt,.md,application/pdf,text/plain,text/markdown"
            multiple
            onChange={(event) => setFiles(Array.from(event.target.files ?? []).slice(0, 10))}
          />
        </label>
        <label>Authority
          <select value={authorityStatus} onChange={(event) => setAuthorityStatus(event.target.value)}>
            <option value="Authoritative">Authoritative</option>
            <option value="Supporting">Supporting</option>
            <option value="Draft">Draft</option>
            <option value="Historical">Historical</option>
            <option value="Amendment">Amendment</option>
          </select>
        </label>
        <label>Business area
          <input value={businessArea} maxLength={200} onChange={(event) => setBusinessArea(event.target.value)} />
        </label>
        <label>Jurisdiction
          <input value={jurisdiction} maxLength={200} onChange={(event) => setJurisdiction(event.target.value)} />
        </label>
        <button className="button primary" type="submit" disabled={!files.length || upload.isPending}>Upload and analyze</button>
        {upload.isPending ? <p role="status">Uploading documents safely...</p> : null}
        {upload.isError ? <p role="alert">The documents could not be uploaded. Check their type, size, and metadata.</p> : null}
      </form>

      <div className="document-workspace-grid">
        <section className="document-task-list" aria-labelledby="document-task-heading">
          <h2 id="document-task-heading">Ingestion tasks</h2>
          {tasks.isPending ? <p role="status">Loading document tasks...</p> : null}
          {tasks.isError ? <p role="alert">Document tasks are unavailable.</p> : null}
          {!tasks.isPending && !taskItems.length ? <p>No document tasks yet.</p> : null}
          <ul>{taskItems.map((task) => <li key={task.taskId}>
            <button
              type="button"
              className={task.taskId === selectedTaskId ? "active" : ""}
              aria-pressed={task.taskId === selectedTaskId}
              onClick={() => setSelectedTaskId(task.taskId)}
            >
              <strong>{task.documents.map((document) => document.safeFilename).join(", ") || "Pending upload"}</strong>
              <span>{task.progress.message}</span>
              <progress value={task.progress.percent} max={100} aria-label={`${task.progress.stage} progress`} />
            </button>
            <div className="document-task-actions">
              <button type="button" onClick={() => cancel.mutate(task.taskId)} disabled={task.status === "cancelled"}>Cancel</button>
              <button type="button" onClick={() => remove.mutate(task.taskId, { onSuccess: () => setSelectedTaskId(null) })}>Delete</button>
            </div>
          </li>)}</ul>
        </section>
        <section className="document-review-region" aria-label="Document recommendation review">
          {selectedTask
            ? <DocumentReview projectId={projectId} taskId={selectedTask.taskId} />
            : <p>Select a task to review its results.</p>}
        </section>
      </div>
    </div>
  );
}

function DocumentReview({ projectId, taskId }: { projectId: string; taskId: string }) {
  const review = useDocumentReview(projectId, taskId);
  const decision = useDocumentReviewDecision(projectId, taskId);
  const [evidenceId, setEvidenceId] = useState<string | null>(null);
  const evidence = useDocumentEvidence(projectId, taskId, evidenceId);
  const evidenceHeading = useRef<HTMLHeadingElement>(null);
  const workspace = review.data;

  useEffect(() => {
    if (evidence.data) evidenceHeading.current?.focus();
  }, [evidence.data]);

  if (review.isPending) return <p role="status">Preparing the review workspace...</p>;
  if (review.isError) return <p role="alert">Review results are not ready. The task may still be processing or may be stale.</p>;
  if (!workspace) return null;
  const submit = (
    recommendationId: string,
    request: Omit<WebDocumentReviewDecision, "expectedWorkKey" | "expectedGraphFingerprint">,
  ) => decision.mutate({
    recommendationId,
    decision: {
      ...request,
      expectedWorkKey: workspace.exactWorkKey,
      expectedGraphFingerprint: workspace.graphFingerprint,
    },
  });

  return <>
    <div className="document-review-summary">
      <h2>Document summary</h2>
      {workspace.summaries.map((summary) => <article key={summary.documentId}>
        <h3>{workspace.documents.find((document) => document.documentId === summary.documentId)?.safeFilename}</h3>
        <p>{summary.purpose}</p>
        <ul>{summary.highlights.map((highlight, index) =>
          <li key={`${summary.documentId}-${index}`}>{highlight}</li>)}</ul>
      </article>)}
      <div className="document-draft-impact" aria-label="Read-only draft impact">
        <strong>Draft impact preview</strong>
        <span>{workspace.draftImpact.acceptedCount} accepted · {workspace.draftImpact.pendingCount} pending · {workspace.draftImpact.blockedCount} blocked</span>
        <small>Read only. Staging becomes available in the next workflow step.</small>
      </div>
    </div>

    {(["OntologyStructure", "BusinessFact"] as const).map((category) =>
      <section key={category} className="document-recommendation-group">
        <h2>{category === "OntologyStructure" ? "Ontology structure" : "Business facts"}</h2>
        {workspace.recommendations.items.filter((item) => item.category === category).map((item) =>
          <RecommendationCard
            key={item.id}
            recommendation={item}
            duplicateOptions={workspace.recommendations.items.filter((candidate) =>
              candidate.id !== item.id && candidate.category === item.category && candidate.type === item.type)}
            onEvidence={setEvidenceId}
            onDecision={(request) => submit(item.id, request)}
            busy={decision.isPending}
          />)}
      </section>)}

    {decision.isError ? <p role="alert">That decision was not saved. Reload the workspace if its results changed.</p> : null}
    {evidenceId ? <EvidenceDialog
      loading={evidence.isPending}
      failed={evidence.isError}
      evidence={evidence.data}
      headingRef={evidenceHeading}
      onClose={() => setEvidenceId(null)}
    /> : null}
  </>;
}

function RecommendationCard({ recommendation, duplicateOptions, onEvidence, onDecision, busy }: {
  recommendation: WebDocumentReviewRecommendation;
  duplicateOptions: WebDocumentReviewRecommendation[];
  onEvidence: (id: string) => void;
  onDecision: (decision: Omit<WebDocumentReviewDecision, "expectedWorkKey" | "expectedGraphFingerprint">) => void;
  busy: boolean;
}) {
  const [label, setLabel] = useState(recommendation.proposedLabel ?? "");
  const [clarification, setClarification] = useState(recommendation.clarification ?? "");
  const [targetSourceId, setTargetSourceId] = useState(recommendation.targetSourceId ?? "");
  const [duplicateId, setDuplicateId] = useState("");
  return <article className="document-recommendation-card">
    <header><div><span>{recommendation.type}</span><h3>{recommendation.proposedLabel ?? recommendation.action}</h3></div><strong>{recommendation.confidenceBand} confidence · {recommendation.confidence}%</strong></header>
    <p>{recommendation.rationale}</p>
    <dl><div><dt>Recommendation</dt><dd>{recommendation.action}</dd></div><div><dt>Status</dt><dd>{recommendation.reviewStatus}</dd></div></dl>
    {recommendation.evidence.length ? <div><strong>Evidence</strong><ul>{recommendation.evidence.map((item) => <li key={item.evidenceId}>
      <button type="button" onClick={() => onEvidence(item.evidenceId)} aria-label={`Open ${item.evidenceType} evidence`}>
        {item.excerpt ?? item.priorRecordId} · {item.extractionMethod ?? "Entio record"}{item.ocrConfidence != null ? ` · OCR ${item.ocrConfidence}%` : ""}
      </button>
    </li>)}</ul></div> : null}
    {recommendation.matches.length ? <label>Ontology match
      <select value={recommendation.selectedMatchIri ?? ""} onChange={(event) => onDecision({ action: "rematch", selectedMatchIri: event.target.value })}>
        <option value="" disabled>Choose a match</option>
        {recommendation.matches.map((match) => <option key={`${match.scope}:${match.entityIri}`} value={match.entityIri}>{match.preferredLabel ?? match.entityIri} · {match.scope} · {match.score}%</option>)}
      </select>
    </label> : null}
    {recommendation.conflicts.map((conflict) => <div className="document-conflict" key={conflict.id}><strong>Conflict requires review</strong><ul>{conflict.alternatives.map((alternative) => <li key={alternative}>{alternative}</li>)}</ul></div>)}
    {recommendation.mandatoryClarificationReasons.length ? <div role="note"><strong>Clarification required</strong><ul>{recommendation.mandatoryClarificationReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul></div> : null}
    {recommendation.priorWorkflowProvenance.length ? <p>Prior workflow evidence: {recommendation.priorWorkflowProvenance.join(", ")}</p> : null}
    <div className="document-review-fields">
      <label>Supported label edit<input value={label} maxLength={500} onChange={(event) => setLabel(event.target.value)} /></label>
      <label>Target ontology source<input value={targetSourceId} maxLength={200} onChange={(event) => setTargetSourceId(event.target.value)} /></label>
      <label>Clarification<textarea value={clarification} maxLength={2000} onChange={(event) => setClarification(event.target.value)} /></label>
      {duplicateOptions.length ? <label>Duplicate recommendation
        <select value={duplicateId} onChange={(event) => setDuplicateId(event.target.value)}>
          <option value="">Choose a duplicate</option>
          {duplicateOptions.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.proposedLabel ?? candidate.id}</option>)}
        </select>
      </label> : null}
    </div>
    <div className="document-review-actions">
      <button type="button" disabled={busy} onClick={() => onDecision({ action: "accept", clarification })}>Accept</button>
      <button type="button" disabled={busy} onClick={() => onDecision({ action: "reject" })}>Reject</button>
      <button type="button" disabled={busy || !clarification.trim()} onClick={() => onDecision({ action: "clarify", clarification })}>Needs clarification</button>
      <button type="button" disabled={busy || !label.trim()} onClick={() => onDecision({ action: "edit", proposedLabel: label, targetSourceId, clarification })}>Save edits</button>
      <button type="button" disabled={busy || !clarification.trim() || recommendation.reconsiderationCount >= 3} onClick={() => onDecision({ action: "reconsider", clarification })}>Reconsider</button>
      {duplicateOptions.length ? <button type="button" disabled={busy || !duplicateId} onClick={() => onDecision({ action: "merge", mergedRecommendationIds: [duplicateId] })}>Merge duplicate</button> : null}
    </div>
  </article>;
}

function EvidenceDialog({ loading, failed, evidence, headingRef, onClose }: {
  loading: boolean;
  failed: boolean;
  evidence: WebDocumentEvidenceView | undefined;
  headingRef: RefObject<HTMLHeadingElement | null>;
  onClose: () => void;
}) {
  return <div className="dialog-backdrop" role="presentation"><section className="document-evidence-dialog" role="dialog" aria-modal="true" aria-labelledby="document-evidence-heading">
    <header><h2 id="document-evidence-heading" ref={headingRef} tabIndex={-1}>Evidence</h2><button type="button" aria-label="Close evidence viewer" onClick={onClose}>×</button></header>
    {loading ? <p role="status">Loading evidence...</p> : null}
    {failed ? <p role="alert">Evidence is unavailable or stale.</p> : null}
    {evidence ? <>
      <p>{evidence.safeFilename}{evidence.pageNumber ? ` · Page ${evidence.pageNumber}` : ""} · {evidence.extractionMethod}{evidence.ocrConfidence != null ? ` · OCR confidence ${evidence.ocrConfidence}%` : ""}</p>
      {evidence.pageImageAvailable ? <p>Safe server-rendered PDF page available. Extracted text is shown for review.</p> : null}
      <pre aria-label="Extracted evidence text"><span>{evidence.text.slice(0, evidence.highlightStart)}</span><mark>{evidence.text.slice(evidence.highlightStart, evidence.highlightEnd)}</mark><span>{evidence.text.slice(evidence.highlightEnd)}</span></pre>
      {evidence.truncated ? <p>Context was truncated to the safe viewing limit.</p> : null}
    </> : null}
  </section></div>;
}
