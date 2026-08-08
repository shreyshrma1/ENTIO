import { useEffect, useMemo, useState } from "react";
import type { WebDomainRecommendationRequest, WebDomainReuseAction } from "../web/projectApi";
import { useContextualDomainRecommendations, useDomainDependencyPreview, useDomainRecommendation } from "../web/queries";

interface DomainRecommendationPanelProps {
  projectId: string;
  draftLabel: string;
  intent: Omit<WebDomainRecommendationRequest, "draftLabel">;
  localTarget?: { iri: string; sourceId: string };
  compact?: boolean;
}

export default function DomainRecommendationPanel({ projectId, draftLabel, intent, localTarget, compact = false }: DomainRecommendationPanelProps) {
  const normalizedLabel = draftLabel.trim();
  const [debouncedLabel, setDebouncedLabel] = useState("");
  const [dismissedIntent, setDismissedIntent] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedAction, setSelectedAction] = useState<WebDomainReuseAction | null>(null);
  const [customLabel, setCustomLabel] = useState("");
  const [customDefinition, setCustomDefinition] = useState("");
  const [extensionIri, setExtensionIri] = useState("");
  const [partialAcknowledged, setPartialAcknowledged] = useState(false);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedLabel(normalizedLabel), 300);
    return () => window.clearTimeout(timeout);
  }, [normalizedLabel]);

  const request = useMemo<WebDomainRecommendationRequest | null>(() => debouncedLabel.length >= 2 ? {
    ...intent,
    draftLabel: debouncedLabel,
    broadSearch: false,
  } : null, [debouncedLabel, intent]);
  const requestKey = request ? JSON.stringify(request) : "";
  const recommendations = useContextualDomainRecommendations(projectId, request);
  const detail = useDomainRecommendation(projectId, selectedId);
  const dependencyPreview = useDomainDependencyPreview(projectId, selectedId, selectedAction);

  useEffect(() => {
    setSelectedId(null);
    setSelectedAction(null);
    setPartialAcknowledged(false);
  }, [requestKey]);

  if (!request || dismissedIntent === requestKey) return null;
  if (recommendations.status.isPending) return <aside className="domain-recommendation-panel compact" role="status">Checking domain ontology status…</aside>;
  if (recommendations.status.data?.status?.availability !== "Active") return null;
  if (recommendations.search.isPending) return <aside className="domain-recommendation-panel compact" role="status">Checking FIBO after typing pauses…</aside>;
  if (recommendations.search.isError) return <aside className="domain-recommendation-panel compact"><p className="muted">Domain recommendations are unavailable. Continue with the local edit.</p></aside>;

  const result = recommendations.search.data?.result;
  if (!result) return null;
  const allLowConfidence = result.recommendations.length > 0 && result.recommendations.every((item) => item.confidence === "Low");
  const list = <RecommendationList recommendations={result.recommendations} selectedId={selectedId} onSelect={(recommendationId) => {
    setSelectedId(recommendationId);
    setSelectedAction(null);
    setPartialAcknowledged(false);
  }} />;

  return <aside className={`domain-recommendation-panel${compact ? " compact" : ""}`} aria-label="Domain ontology recommendations">
    <div className="section-heading compact"><div><span className="overline">Domain reuse</span><h3>Related FIBO concepts</h3></div><button className="button small" type="button" onClick={() => setDismissedIntent(requestKey)}>Dismiss</button></div>
    <p className="muted">Suggestions are server ranked. They never replace or submit this form automatically.</p>
    {result.availability === "LexicalStructural" ? <p className="retrieval-status retrieval-status-lexicalstructural">Lexical and structural retrieval</p> : null}
    {result.availability === "Unavailable" ? <p className="muted">Domain retrieval unavailable. Continue with the local edit.</p> : null}
    {result.noConfidentMatch ? <p>No confident domain match. The local action remains available.</p> : null}
    {allLowConfidence ? <details><summary>Possible low-confidence matches ({result.recommendations.length})</summary>{list}</details> : list}
    {selectedId ? <RecommendationActions
      detail={detail}
      action={selectedAction}
      onAction={(action: WebDomainReuseAction) => {
        if (action === "ContinueLocally") {
          setDismissedIntent(requestKey);
          return;
        }
        setSelectedAction(action);
      }}
      dependencyPreview={dependencyPreview}
      localTarget={localTarget}
      targetSourceId={intent.targetSourceId}
      customLabel={customLabel}
      customDefinition={customDefinition}
      extensionIri={extensionIri}
      onCustomLabel={setCustomLabel}
      onCustomDefinition={setCustomDefinition}
      onExtensionIri={setExtensionIri}
      partialAcknowledged={partialAcknowledged}
      onPartialAcknowledged={setPartialAcknowledged}
      pending={recommendations.stage.isPending}
      success={recommendations.stage.isSuccess}
      error={recommendations.stage.isError}
      onConfirm={() => {
        if (!selectedAction) return;
        const stageRequest = {
          action: selectedAction,
          ...(selectedAction === "ReuseAndCustomize" ? { customization: { preferredLabel: customLabel.trim() || debouncedLabel, definition: customDefinition.trim() || undefined } } : {}),
          ...(selectedAction === "ExtendLocally" ? { localIri: extensionIri.trim(), localSourceId: localTarget?.sourceId ?? intent.targetSourceId } : {}),
          ...(selectedAction === "MapClose" || selectedAction === "MapRelated" ? { localIri: localTarget?.iri, localSourceId: localTarget?.sourceId } : {}),
          partialMaterializationAcknowledged: partialAcknowledged,
        };
        recommendations.stage.mutate({ recommendationId: selectedId, stageRequest });
      }}
    /> : null}
  </aside>;
}

function RecommendationList({ recommendations, selectedId, onSelect }: { recommendations: Array<{ recommendationId: string; preferredLabel: string; kind: string; sourceFamily: string; confidence: string; reasons: Array<{ type: string }>; warnings: string[] }>; selectedId: string | null; onSelect: (id: string) => void }) {
  if (!recommendations.length) return <p>No domain matches.</p>;
  return <div className="domain-context-results">{recommendations.map((item) => <button type="button" className={selectedId === item.recommendationId ? "selected-list-item" : "list-item"} key={item.recommendationId} onClick={() => onSelect(item.recommendationId)}><strong>{item.preferredLabel}</strong><small>{item.sourceFamily === "OMG_COMMONS" ? "OMG Commons" : "FIBO"} · {humanize(item.kind)} · {item.confidence}</small></button>)}</div>;
}

interface RecommendationActionsProps {
  detail: ReturnType<typeof useDomainRecommendation>;
  action: WebDomainReuseAction | null;
  onAction: (action: WebDomainReuseAction) => void;
  dependencyPreview: ReturnType<typeof useDomainDependencyPreview>;
  localTarget?: { iri: string; sourceId: string };
  targetSourceId?: string;
  customLabel: string;
  customDefinition: string;
  extensionIri: string;
  onCustomLabel: (value: string) => void;
  onCustomDefinition: (value: string) => void;
  onExtensionIri: (value: string) => void;
  partialAcknowledged: boolean;
  onPartialAcknowledged: (value: boolean) => void;
  pending: boolean;
  success: boolean;
  error: boolean;
  onConfirm: () => void;
}

function RecommendationActions({ detail, action, onAction, dependencyPreview, localTarget, targetSourceId, customLabel, customDefinition, extensionIri, onCustomLabel, onCustomDefinition, onExtensionIri, partialAcknowledged, onPartialAcknowledged, pending, success, error, onConfirm }: RecommendationActionsProps) {
  if (detail.isPending) return <p role="status">Loading source and project comparison…</p>;
  if (detail.isError) return <p role="alert">This recommendation is stale. Pause typing to refresh it.</p>;
  if (!detail.data) return null;
  const recommendation = detail.data.recommendation;
  const difference = detail.data.difference;
  const partial = difference.sourceSnapshot.classification === "PartialMaterialization";
  const reuseAllowed = recommendation.permittedActions.includes("Reuse");
  const extendAllowed = recommendation.permittedActions.includes("Extend");
  const mapAllowed = recommendation.permittedActions.includes("MapAnnotation") && Boolean(localTarget);
  const actionReady = action === "Reuse" || action === "ReuseAndCustomize" ||
    (action === "ExtendLocally" && extensionIri.trim() && (localTarget?.sourceId || targetSourceId)) ||
    ((action === "MapClose" || action === "MapRelated") && localTarget);
  return <section className="domain-context-detail" aria-live="polite">
    <p><strong>Canonical IRI:</strong> <code>{recommendation.iri}</code></p>
    <div className="domain-meaning-grid"><section><h4>FIBO source</h4><p>{difference.sourceSnapshot.statements.length} supported statements.</p></section><section><h4>Current project</h4><p>{difference.projectStatements.length ? `${difference.projectStatements.length} current statements.` : "Not currently reused."}</p><p>{humanize(difference.classification)}</p></section></div>
    {recommendation.warnings.includes("CustomizedFromSource") ? <p className="domain-advisory">The project meaning has been customized from the current FIBO source.</p> : null}
    <div className="domain-action-row">
      {reuseAllowed ? <><button type="button" onClick={() => onAction("Reuse")}>Reuse</button><button type="button" onClick={() => onAction("ReuseAndCustomize")}>Customize</button></> : null}
      {extendAllowed ? <button type="button" onClick={() => onAction("ExtendLocally")}>Extend</button> : null}
      {mapAllowed ? <><button type="button" onClick={() => onAction("MapClose")}>Map close</button><button type="button" onClick={() => onAction("MapRelated")}>Map related</button></> : null}
      <button type="button" onClick={() => onAction("ContinueLocally")}>Continue locally</button>
    </div>
    {action === "ReuseAndCustomize" ? <div className="domain-customization-fields"><label>Project label<input value={customLabel} onChange={(event) => onCustomLabel(event.target.value)} placeholder={recommendation.preferredLabel} /></label><label>Project definition<textarea value={customDefinition} onChange={(event) => onCustomDefinition(event.target.value)} /></label></div> : null}
    {action === "ExtendLocally" ? <label>New local IRI<input value={extensionIri} onChange={(event) => onExtensionIri(event.target.value)} placeholder="https://example.com/ontology#SpecializedConcept" /></label> : null}
    {action && action !== "ContinueLocally" ? <div className="domain-action-preview"><h4>Server preview</h4>{dependencyPreview.isPending ? <p role="status">Checking dependencies…</p> : null}{dependencyPreview.isError ? <p role="alert">The action preview is stale or unavailable.</p> : null}{dependencyPreview.data ? <p>{dependencyPreview.data.dependencyIris.length} required domain dependencies. The existing form remains unchanged.</p> : null}{partial ? <label><input type="checkbox" checked={partialAcknowledged} onChange={(event) => onPartialAcknowledged(event.target.checked)} /> I reviewed the omitted source axioms.</label> : null}<button className="button primary" type="button" disabled={!actionReady || pending || dependencyPreview.isPending || dependencyPreview.isError || (partial && !partialAcknowledged)} onClick={onConfirm}>Stage domain action</button></div> : null}
    {success ? <p role="status">Domain action added to the shared review queue.</p> : null}
    {error ? <p role="alert">The domain action could not be staged. Refresh the recommendation and try again.</p> : null}
  </section>;
}

function humanize(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ");
}
