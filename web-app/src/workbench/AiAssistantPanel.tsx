import { FormEvent, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  queryKeys,
  useAiConversation,
  useAiConversationActions,
  useAiConversations,
  useAiProviderSettings,
  useAiDraft,
  useAiDraftActions,
} from "../web/queries";
import { streamAiRunEvents } from "../web/projectApi";
import type {
  WebAiConversationDecision,
  WebAiConversationTurnResponse,
  WebAiRun,
  WebAiRunEvent,
} from "../web/contracts";
import type { WebEntityReference } from "../web/projectApi";
import AiDraftReview from "./ai/AiDraftReview";
import AiRunTimeline, { type AiStreamState } from "./ai/AiRunTimeline";

export default function AiAssistantPanel({ projectId, entity }: { projectId: string; entity?: WebEntityReference | null }) {
  const queryClient = useQueryClient();
  const providerSettings = useAiProviderSettings();
  const conversations = useAiConversations(projectId);
  const conversationActions = useAiConversationActions(projectId);
  const draftActions = useAiDraftActions(projectId);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const conversation = useAiConversation(projectId, activeConversationId);
  const [message, setMessage] = useState("");
  const [clarification, setClarification] = useState("");
  const [planRevision, setPlanRevision] = useState("");
  const [latestTurn, setLatestTurn] = useState<WebAiConversationTurnResponse | null>(null);
  const [activeRun, setActiveRun] = useState<WebAiRun | null>(null);
  const [runEvents, setRunEvents] = useState<WebAiRunEvent[]>([]);
  const [streamState, setStreamState] = useState<AiStreamState>("idle");
  const [streamError, setStreamError] = useState<string | null>(null);
  const activeRunId = useRef<string | null>(null);
  const lastEventId = useRef<string | undefined>(undefined);
  const composer = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    setActiveConversationId(null);
    setLatestTurn(null);
    setActiveRun(null);
    setRunEvents([]);
    setStreamState("idle");
    activeRunId.current = null;
    lastEventId.current = undefined;
  }, [projectId]);

  useEffect(() => {
    if (!activeConversationId && conversations.data?.conversations.length) {
      setActiveConversationId(conversations.data.conversations[0].id);
    }
  }, [activeConversationId, conversations.data]);

  useEffect(() => {
    if (conversation.data?.conversation.id === activeConversationId) composer.current?.focus();
  }, [activeConversationId, conversation.data?.conversation.id]);

  useEffect(() => {
    if (!activeRun) return;
    const controller = new AbortController();
    let resynchronizing = false;
    setStreamState("connecting");
    setStreamError(null);
    streamAiRunEvents(projectId, activeRun.id, {
      lastEventId: lastEventId.current,
      signal: controller.signal,
      onEvent: (event, eventId) => {
        if (eventId) lastEventId.current = eventId;
        setStreamState("connected");
        setRunEvents((current) => [...current.filter((item) => item.sequence !== event.sequence), event].sort((left, right) => left.sequence - right.sequence));
      },
      onResynchronization: () => {
        resynchronizing = true;
        lastEventId.current = undefined;
        setStreamState("reconnecting");
        queryClient.invalidateQueries({ queryKey: queryKeys.aiConversation(projectId, activeRun.conversationId) });
        queryClient.invalidateQueries({ queryKey: queryKeys.aiRun(projectId, activeRun.id) });
        const draftId = latestTurn?.draftId ?? conversation.data?.conversation.currentDraftId;
        if (draftId) queryClient.invalidateQueries({ queryKey: queryKeys.aiDraft(projectId, draftId) });
      },
    }).then(() => {
      if (!controller.signal.aborted) setStreamState(resynchronizing ? "connected" : "idle");
    }).catch((error: unknown) => {
      if (controller.signal.aborted) return;
      setStreamState("disconnected");
      setStreamError(error instanceof Error ? error.message : "The private event stream disconnected.");
    });
    return () => controller.abort();
  }, [activeRun?.id, activeRun?.updatedAt, conversation.data?.conversation.currentDraftId, latestTurn?.draftId, projectId, queryClient]);

  const currentConversation = conversation.data?.conversation;
  const draftId = latestTurn?.draftId ?? currentConversation?.currentDraftId ?? null;
  const draft = useAiDraft(projectId, draftId);
  const analysis = draftActions.analyze.data?.analysis ?? null;
  const submission = draftActions.submit.data ?? null;
  const runIdForSubmission = activeRun?.id ?? draft.data?.draft.items.map((item) => item.runId).find(Boolean) ?? null;

  function beginConversation() {
    conversationActions.create.mutate(undefined, {
      onSuccess: (response) => {
        setActiveConversationId(response.conversation.id);
        setLatestTurn(null);
        setActiveRun(null);
        setRunEvents([]);
      },
    });
  }

  function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    sendDecision("MESSAGE", message);
  }

  function sendDecision(decision: WebAiConversationDecision, content: string) {
    const trimmed = content.trim();
    if (!activeConversationId || !trimmed) return;
    draftActions.analyze.reset();
    draftActions.submit.reset();
    conversationActions.send.mutate({
      conversationId: activeConversationId,
      idempotencyKey: requestId("message"),
      request: {
        message: trimmed,
        decision,
        screenContext: {
          screen: "EXPLORE",
          selectedEntityIri: entity?.iri,
          selectedSourceId: entity?.sourceId ?? undefined,
        },
      },
    }, {
      onSuccess: (turn) => {
        setLatestTurn(turn);
        if (activeRunId.current !== turn.run.id) {
          activeRunId.current = turn.run.id;
          lastEventId.current = undefined;
          setRunEvents([]);
        }
        setActiveRun(turn.run);
        setMessage("");
        setClarification("");
        setPlanRevision("");
        if (turn.draftId) queryClient.invalidateQueries({ queryKey: queryKeys.aiDraft(projectId, turn.draftId) });
      },
    });
  }

  function cancelRun() {
    if (!activeRun) return;
    conversationActions.cancel.mutate(activeRun.id, { onSuccess: (response) => setActiveRun(response.run) });
  }

  function analyzeDraft() {
    if (draftId) draftActions.analyze.mutate(draftId);
  }

  function submitDraft() {
    if (!draftId || !analysis || !runIdForSubmission || !analysis.previewGraphFingerprint) return;
    draftActions.submit.mutate({
      draftId,
      idempotencyKey: requestId("submit"),
      request: {
        analysisId: analysis.id,
        runId: runIdForSubmission,
        rationale: "Submitted from the Entio AI private draft after deterministic analysis.",
        expectedBaselineFingerprint: analysis.baselineFingerprint,
        expectedDraftFingerprint: analysis.draftFingerprint,
        expectedPreviewGraphFingerprint: analysis.previewGraphFingerprint,
        expectedAnalysisReferenceIds: analysis.references.map((reference) => reference.id),
      },
    });
  }

  const aiReady = providerSettings.data?.selectionStatus === "READY";
  const credentialMissing = providerSettings.isSuccess && providerSettings.data.credentialStatus === "NOT_CONFIGURED";
  const modelSelectionRequired = providerSettings.isSuccess && providerSettings.data.credentialStatus === "VALID" && !aiReady;
  const runActive = Boolean(activeRun && !terminalRunStatus(activeRun.status));
  const canSubmit = Boolean(analysis?.readyForReview && analysis.previewGraphFingerprint && runIdForSubmission && draft.data?.draft.status === "READY_FOR_REVIEW");

  return (
    <section className="content-band ai-assistant-panel" aria-labelledby="ai-assistant-heading">
      <div className="ai-context-header">
        <div><p className="eyebrow">Tool-driven copilot</p><h2 id="ai-assistant-heading">Conversation</h2></div>
        <span className={`ai-state ${aiReady ? "ai-state-ready" : "ai-state-unavailable"}`}>{providerSettings.isPending ? "Checking AI settings" : aiReady ? `Ready · ${providerSettings.data?.selectedModel?.displayName ?? "verified model"}` : "AI unavailable"}</span>
      </div>
      <div className="ai-context-chips" aria-label="Assistant context"><span>{projectId}</span>{entity ? <span>{entity.label}</span> : <span>Project context</span>}</div>
      {entity ? <details className="ai-context-details"><summary>Technical context</summary><code>{entity.iri}</code></details> : null}
      {providerSettings.isError ? <p role="alert">AI provider settings are unavailable. Non-AI workbench features remain available.</p> : null}
      {credentialMissing ? <div className="ai-unavailable" role="status"><strong>Add an OpenAI credential in Settings to use the copilot.</strong><p>The rest of the workbench remains available.</p></div> : null}
      {modelSelectionRequired ? <div className="ai-unavailable" role="status"><strong>Select and verify an available model in Settings to use the copilot.</strong><p>A configured credential alone does not make AI ready. The rest of the workbench remains available.</p></div> : null}
      {conversations.isPending ? <p role="status">Loading conversations...</p> : null}
      {conversations.isError ? <p role="alert">Could not load conversations. {conversations.error.message}</p> : null}
      <div className="ai-conversation-controls">
        {conversations.data?.conversations.length ? <label htmlFor="ai-conversation">Conversation<select id="ai-conversation" value={activeConversationId ?? ""} onChange={(event) => { setActiveConversationId(event.target.value); setLatestTurn(null); setActiveRun(null); setRunEvents([]); }}>{conversations.data.conversations.map((item, index) => <option key={item.id} value={item.id}>Conversation {conversations.data.conversations.length - index}</option>)}</select></label> : null}
        <button className="button" type="button" onClick={beginConversation} disabled={conversationActions.create.isPending || !aiReady}>{conversationActions.create.isPending ? "Starting..." : "New conversation"}</button>
      </div>
      {conversationActions.create.isError ? <p role="alert">Could not start a conversation. {conversationActions.create.error.message}</p> : null}
      {activeConversationId ? <>
        {conversation.isPending ? <p role="status">Loading message history...</p> : null}
        {conversation.isError ? <p role="alert">Message history unavailable. {conversation.error.message}</p> : null}
        <ConversationHistory messages={currentConversation?.messages ?? []} />
        {conversationActions.send.isPending ? <div className="ai-thinking" role="status" aria-live="polite"><strong>Entio AI is working…</strong><p>It may be inspecting ontology entities, preparing draft changes, or waiting briefly for provider capacity.</p></div> : null}
        {latestTurn?.plan ? <PlanConfirmation plan={latestTurn.plan} revision={planRevision} onRevisionChange={setPlanRevision} onConfirm={() => sendDecision("CONFIRM_PLAN", "Confirm this plan.")} onRevise={() => sendDecision("REVISE_PLAN", planRevision)} onCancel={cancelRun} busy={conversationActions.send.isPending || conversationActions.cancel.isPending} /> : null}
        {latestTurn?.clarificationQuestion ? <Clarification question={latestTurn.clarificationQuestion} answer={clarification} onAnswerChange={setClarification} onSubmit={() => sendDecision("ANSWER_CLARIFICATION", clarification)} onCancel={cancelRun} busy={conversationActions.send.isPending || conversationActions.cancel.isPending} /> : null}
        <form className="ai-composer" onSubmit={send}><label htmlFor="ai-message">Ask about this ontology context</label><textarea ref={composer} id="ai-message" value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Explain a concept or describe an ontology change..." rows={3} disabled={!aiReady || conversationActions.send.isPending || runActive && activeRun?.status !== "AWAITING_PLAN_CONFIRMATION" && activeRun?.status !== "AWAITING_CLARIFICATION"} /><div className="ai-composer-actions"><span>Structured project context only</span>{runActive ? <button className="button" type="button" onClick={cancelRun} disabled={conversationActions.cancel.isPending}>Cancel run</button> : null}<button className="button primary" type="submit" disabled={!aiReady || !message.trim() || conversationActions.send.isPending || runActive}>{conversationActions.send.isPending ? "Sending..." : "Send"}</button></div></form>
        {conversationActions.send.isError ? <p role="alert">Assistant request failed. {conversationActions.send.error.message}</p> : null}
        {conversationActions.cancel.isError ? <p role="alert">Could not cancel the run. {conversationActions.cancel.error.message}</p> : null}
        {activeRun ? <div className="ai-run-status"><strong>{activeRun.status.replaceAll("_", " ")}</strong><span>{activeRun.capabilityCallCount} capabilities · {activeRun.draftEditCount} draft edits · {activeRun.correctionCycleCount} corrections</span></div> : null}
        {latestTurn?.limits.length ? <ul className="ai-limits" aria-label="Run limits">{latestTurn.limits.map((limit) => <li key={limit.kind}>{limit.kind}: {limit.observed} of {limit.maximum}</li>)}</ul> : null}
        {streamError ? <p role="alert">Activity stream disconnected. {streamError}</p> : null}
        <AiRunTimeline events={runEvents} streamState={streamState} />
        <AiDraftReview
          draft={draft.data?.draft ?? null}
          draftPending={draft.isPending && Boolean(draftId)}
          draftError={draft.isError ? draft.error.message : null}
          analysis={analysis}
          analysisPending={draftActions.analyze.isPending}
          analysisError={draftActions.analyze.isError ? draftActions.analyze.error.message : null}
          submission={submission}
          submissionPending={draftActions.submit.isPending}
          submissionError={draftActions.submit.isError ? draftActions.submit.error.message : null}
          canSubmit={canSubmit}
          onAnalyze={analyzeDraft}
          onSubmit={submitDraft}
        />
      </> : <div className="ai-empty"><strong>No conversation selected</strong><p>Start a private project-scoped conversation. Messages and drafts remain in this server session.</p></div>}
    </section>
  );
}

function ConversationHistory({ messages }: { messages: Array<{ id: string; role: string; content: string; operation: string | null; evidenceReferenceIds: string[] }> }) {
  if (!messages.length) return <div className="ai-empty"><strong>Ready when you are</strong><p>Ask for an explanation, a plan, or a typed ontology draft.</p></div>;
  return <ol className="ai-messages" aria-label="Conversation messages">{messages.map((item) => <li key={item.id} className={`ai-message ai-message-${item.role.toLowerCase()}`}><span>{item.role === "USER" ? "You" : item.role === "ASSISTANT" ? "Entio AI" : "Capability"}</span><p>{item.content}</p>{item.operation || item.evidenceReferenceIds.length ? <details><summary>Evidence and provenance</summary>{item.operation ? <p>{item.operation}</p> : null}{item.evidenceReferenceIds.length ? <ul>{item.evidenceReferenceIds.map((reference) => <li key={reference}>{reference}</li>)}</ul> : null}</details> : null}</li>)}</ol>;
}

function PlanConfirmation({ plan, revision, onRevisionChange, onConfirm, onRevise, onCancel, busy }: { plan: NonNullable<WebAiConversationTurnResponse["plan"]>; revision: string; onRevisionChange: (value: string) => void; onConfirm: () => void; onRevise: () => void; onCancel: () => void; busy: boolean }) {
  return <section className="ai-decision" aria-labelledby="ai-plan-heading"><h3 id="ai-plan-heading">Confirm the plan</h3><p>{plan.request}</p><ol>{plan.steps.map((step) => <li key={step}>{step}</li>)}</ol>{plan.openDecisions.length ? <ul className="ai-warning-list">{plan.openDecisions.map((decision) => <li key={decision}>{decision}</li>)}</ul> : null}<label htmlFor="ai-plan-revision">Revision request<textarea id="ai-plan-revision" rows={2} value={revision} onChange={(event) => onRevisionChange(event.target.value)} placeholder="Describe what should change in the plan" /></label><div className="button-row"><button className="button primary" type="button" onClick={onConfirm} disabled={busy}>Confirm plan</button><button className="button" type="button" onClick={onRevise} disabled={busy || !revision.trim()}>Revise plan</button><button className="button danger" type="button" onClick={onCancel} disabled={busy}>Cancel</button></div></section>;
}

function Clarification({ question, answer, onAnswerChange, onSubmit, onCancel, busy }: { question: string; answer: string; onAnswerChange: (value: string) => void; onSubmit: () => void; onCancel: () => void; busy: boolean }) {
  return <section className="ai-decision" aria-labelledby="ai-clarification-heading"><h3 id="ai-clarification-heading">Clarification needed</h3><p>{question}</p><label htmlFor="ai-clarification">Your answer<textarea id="ai-clarification" rows={2} value={answer} onChange={(event) => onAnswerChange(event.target.value)} /></label><div className="button-row"><button className="button primary" type="button" onClick={onSubmit} disabled={busy || !answer.trim()}>Answer clarification</button><button className="button danger" type="button" onClick={onCancel} disabled={busy}>Cancel</button></div></section>;
}

function terminalRunStatus(status: string): boolean {
  return ["READY_FOR_REVIEW", "FAILED", "CANCELLED", "LIMIT_REACHED", "STALE"].includes(status);
}

function requestId(prefix: string): string {
  const id = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${id}`;
}
