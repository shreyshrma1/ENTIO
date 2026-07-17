import type {
  WebAiDraft,
  WebAiDraftAnalysis,
  WebAiReviewSubmissionResponse,
} from "../../web/contracts";

interface AiDraftReviewProps {
  draft: WebAiDraft | null;
  draftPending: boolean;
  draftError: string | null;
  analysis: WebAiDraftAnalysis | null;
  analysisPending: boolean;
  analysisError: string | null;
  submission: WebAiReviewSubmissionResponse | null;
  submissionPending: boolean;
  submissionError: string | null;
  canSubmit: boolean;
  onAnalyze: () => void;
  onSubmit: () => void;
}

export default function AiDraftReview(props: AiDraftReviewProps) {
  if (props.draftPending) return <p role="status">Loading private draft...</p>;
  if (props.draftError) return <p role="alert">Private draft unavailable. {props.draftError}</p>;
  if (!props.draft) return <div className="ai-empty"><strong>No private draft</strong><p>Ask the assistant to prepare a typed ontology change. Nothing enters the shared review queue until you submit it.</p></div>;
  const draft = props.draft;
  const blockingState = draft.status === "STALE" || draft.status === "CONFLICTED" || draft.status === "INVALID";
  return (
    <section className="ai-draft" aria-labelledby="ai-draft-heading">
      <div className="ai-subheading"><h3 id="ai-draft-heading">Private draft</h3><span className={`ai-state ai-state-${draft.status.toLowerCase().replaceAll("_", "-")}`}>{draft.status.replaceAll("_", " ")}</span></div>
      {blockingState ? <p className="ai-warning" role="alert">This draft is {draft.status.toLowerCase()}. Revise it and rerun deterministic analysis before submission.</p> : null}
      {draft.items.length ? <ol className="ai-draft-items">
        {draft.items.map((item) => <li key={item.id}><div><strong>{item.summary}</strong><span>{item.capabilityName} · {item.targetSourceId}</span></div><p>{item.rationale}</p>{item.dependencyItemIds.length ? <details><summary>Dependencies</summary><ul>{item.dependencyItemIds.map((id) => <li key={id}>{id}</li>)}</ul></details> : null}</li>)}
      </ol> : <p className="muted">The draft has no typed edits yet.</p>}
      {draft.revisions.length ? <details className="ai-revisions"><summary>Revision history ({draft.revisions.length})</summary><ol>{draft.revisions.map((revision) => <li key={revision.revision}><strong>Revision {revision.revision}: {revision.action}</strong><span>{revision.explanation}</span></li>)}</ol></details> : null}
      <button className="button" type="button" onClick={props.onAnalyze} disabled={props.analysisPending || !draft.items.length || draft.status === "SUBMITTED"}>{props.analysisPending ? "Analyzing..." : "Run deterministic analysis"}</button>
      {props.analysisError ? <p role="alert">Draft analysis failed. {props.analysisError}</p> : null}
      {props.analysis ? <AnalysisSummary analysis={props.analysis} /> : null}
      {props.submissionError ? <p role="alert">Could not submit for review. {props.submissionError}</p> : null}
      {props.submission ? <div className="ai-submission" role="status"><strong>Submitted for human review</strong><p>Proposal {props.submission.proposalId} is {props.submission.reviewState.toLowerCase()}.</p><a className="button primary" href={props.submission.reviewRoute}>Open proposal review</a></div> : <button className="button primary" type="button" onClick={props.onSubmit} disabled={!props.canSubmit || props.submissionPending}>{props.submissionPending ? "Submitting..." : "Submit for human review"}</button>}
      <details className="technical-details"><summary>Technical draft details</summary><dl><div><dt>Draft ID</dt><dd><code>{draft.id}</code></dd></div><div><dt>Allowed sources</dt><dd>{draft.allowedSourceIds.join(", ")}</dd></div><div><dt>Baseline</dt><dd><code>{draft.baselineFingerprint}</code></dd></div></dl></details>
    </section>
  );
}

function AnalysisSummary({ analysis }: { analysis: WebAiDraftAnalysis }) {
  return <section className={`ai-analysis ai-analysis-${analysis.status.toLowerCase()}`} aria-labelledby="ai-analysis-heading"><div className="ai-subheading"><h4 id="ai-analysis-heading">Deterministic analysis</h4><span>{analysis.status.replaceAll("_", " ")}</span></div><p>{analysis.readyForReview ? "Ready for human review." : "Not ready for review."} Validation {analysis.validationOk ? "passed" : "did not pass"}.</p>{analysis.findings.length ? <ul className="ai-findings">{analysis.findings.map((finding) => <li key={finding.id}><strong>{finding.severity}</strong><span>{finding.message}</span></li>)}</ul> : <p className="muted">No validation findings.</p>}{analysis.diff.length ? <details open><summary>Semantic diff ({analysis.diff.length})</summary><ul className="ai-diff">{analysis.diff.map((entry, index) => <li key={`${entry.kind}:${index}`}>{entry.description}</li>)}</ul></details> : null}{analysis.references.length ? <details><summary>Analysis provenance</summary><ul>{analysis.references.map((reference) => <li key={`${reference.stage}:${reference.id}`}>{reference.stage}: {reference.id}</li>)}</ul></details> : null}</section>;
}
