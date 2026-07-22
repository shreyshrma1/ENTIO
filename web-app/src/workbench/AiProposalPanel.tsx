import { useEffect, useRef, useState } from "react";
import {
  cancelAiProposal,
  listAiChats,
  loadAiProposal,
  rejectAiProposal,
  removeAiProposalEdit,
  stageAiProposal,
  startAiProposal,
  type WebAiProposalEdit,
  type WebAiProposalRunResponse,
} from "../web/projectApi";
import MarkdownContent from "../components/ui/MarkdownContent";

const terminal = new Set(["READY", "FAILED", "CANCELLED", "STAGED", "REJECTED"]);
const closed = new Set(["STAGED", "REJECTED"]);

export default function AiProposalPanel({ projectId, onStaged, stagedAiRunIds = [], compact = false }: { projectId: string; onStaged: () => void; stagedAiRunIds?: string[]; compact?: boolean }) {
  const [prompt, setPrompt] = useState("");
  const [run, setRun] = useState<WebAiProposalRunResponse | null>(null);
  const [statusOpen, setStatusOpen] = useState(false);
  const [proposalOpen, setProposalOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<Awaited<ReturnType<typeof listAiChats>>>([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const conversationEnd = useRef<HTMLDivElement>(null);

  const activeChatKey = `entio.ai.active-chat.${projectId}`;

  useEffect(() => {
    let cancelled = false;
    async function restoreChat() {
      try {
        const chats = await listAiChats(projectId);
        if (cancelled) return;
        setHistory(chats);
        const savedRunId = window.localStorage.getItem(activeChatKey);
        const selected = chats.find((chat) => chat.runId === savedRunId) ?? chats[0];
        if (selected) setRun(await loadAiProposal(projectId, selected.runId));
      } catch (failure) {
        if (!cancelled) setError(failure instanceof Error ? failure.message : "Could not restore AI chat history.");
      }
    }
    void restoreChat();
    return () => { cancelled = true; };
  }, [projectId, activeChatKey]);

  useEffect(() => {
    if (run) window.localStorage.setItem(activeChatKey, run.runId);
  }, [activeChatKey, run?.runId]);

  useEffect(() => {
    if (!run || terminal.has(run.status)) return undefined;
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const next = await loadAiProposal(projectId, run.runId);
        if (!cancelled) setRun(next);
      } catch (failure) {
        if (!cancelled) setError(failure instanceof Error ? failure.message : "Could not read the AI run.");
      }
    }, 750);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [projectId, run]);

  useEffect(() => {
    if (!run || run.status !== "STAGED" || stagedAiRunIds.includes(run.runId)) return;
    void loadAiProposal(projectId, run.runId).then(setRun).catch((failure) => setError(failure instanceof Error ? failure.message : "Could not restore the AI proposal."));
  }, [projectId, run?.runId, run?.status, stagedAiRunIds]);

  useEffect(() => {
    conversationEnd.current?.scrollIntoView?.({ block: "nearest" });
  }, [run?.messages, run?.message, run?.status]);

  async function submit() {
    if (!prompt.trim()) return;
    setError(null);
    try {
      const next = await startAiProposal(projectId, prompt.trim(), run && !closed.has(run.status) ? run.runId : undefined);
      setRun(next);
      setHistory(await listAiChats(projectId));
      setPrompt("");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Could not start the AI proposal.");
    }
  }

  function newChat() {
    setRun(null);
    setPrompt("");
    setProposalOpen(false);
    setStatusOpen(false);
    window.localStorage.removeItem(activeChatKey);
  }

  async function openChat(runId: string) {
    try {
      setRun(await loadAiProposal(projectId, runId));
      setHistoryOpen(false);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Could not open that AI chat.");
    }
  }

  async function stop() {
    if (!run) return;
    try { setRun(await cancelAiProposal(projectId, run.runId)); } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not stop the run."); }
  }

  async function removeEdit(editId: string) {
    if (!run) return;
    try { setRun(await removeAiProposalEdit(projectId, run.runId, editId)); } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not remove the edit."); }
  }

  async function stage() {
    if (!run) return;
    try { const next = await stageAiProposal(projectId, run.runId); setRun(next); setProposalOpen(false); onStaged(); } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not stage the proposal."); }
  }

  async function reject() {
    if (!run) return;
    try { setRun(await rejectAiProposal(projectId, run.runId)); setProposalOpen(false); } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not reject the proposal."); }
  }

  // Keep the assistant usable while a browser tab is talking to a server that
  // may have been restarted during development. Older in-memory responses can
  // omit optional collections; rendering must never turn that into a fatal
  // React error.
  const working = run !== null && !terminal.has(run.status);
  const messages = Array.isArray(run?.messages) ? run.messages : [];
  const edits = Array.isArray(run?.edits) ? run.edits : [];
  const validationMessages = Array.isArray(run?.validation?.messages) ? run.validation.messages : [];
  return <div className={`ai-proposal-panel ${compact ? "ai-proposal-panel-compact" : ""}`}>
    {!compact ? <header className="ai-panel-header"><div><span className="overline">Native AI</span><h1>Ontology assistant</h1><p>Ask about the ontology or describe a change. Entio answers questions directly and prepares review-only edits when needed.</p></div><span className={`ai-run-state ${working ? "working" : ""}`} role="status">{run?.status ?? "Ready"}</span></header> : <div className="ai-compact-status"><span>{working ? "Working" : "Ready"}</span><div className="ai-chat-actions"><button type="button" onClick={newChat}>New chat</button><button type="button" onClick={() => setHistoryOpen(true)}>History</button>{edits.length && run?.status !== "STAGED" ? <button type="button" onClick={() => setProposalOpen(true)}>{edits.length} proposed</button> : null}</div></div>}
    <div className="ai-conversation" aria-live="polite">
      {!run ? <div className="ai-empty"><strong>Ready when you are</strong><span>Plans, investigates, and drafts without changing source files.</span></div> : <>
        {messages.map((message, index) => <div className={`ai-message ${message.role === "user" ? "ai-message-user" : "ai-message-assistant"}`} key={`${message.timestamp}-${index}`}><span className="ai-message-role">{message.role === "user" ? "YOU" : "ENTIO AI"}</span>{message.role === "assistant" ? <MarkdownContent>{message.content}</MarkdownContent> : <div className="ai-message-text">{message.content}</div>}{message.evidence?.length ? <div className="ai-evidence"><strong>Evidence</strong>{message.evidence.map((item, evidenceIndex) => <code key={`${item.subject}-${item.predicate}-${evidenceIndex}`}>{item.subject} — {item.predicate} — {item.objectValue}</code>)}</div> : null}</div>)}
        {run.message || working ? <div className="ai-message ai-message-assistant"><span className="ai-message-role">ENTIO AI</span>{run.message ?? "Entio is working…"}</div> : null}
        <div ref={conversationEnd} />
      </>}
    </div>
    <div className="ai-panel-actions"><button className="button" type="button" onClick={() => setStatusOpen(true)} disabled={!run}>Status Updates</button><button className="button" type="button" onClick={() => setProposalOpen(true)} disabled={!run || run.status === "STAGED" || !edits.length}>View Proposal{edits.length && run?.status !== "STAGED" ? ` (${edits.length})` : ""}</button>{working ? <button className="button danger" type="button" onClick={stop}>Stop generation</button> : null}</div>
    <form className="ai-prompt-form" onSubmit={(event) => { event.preventDefault(); void submit(); }}><label className={compact ? "visually-hidden" : undefined} htmlFor="ai-proposal-prompt">Ask Entio about the ontology or model a change</label><textarea id="ai-proposal-prompt" value={prompt} onChange={(event) => setPrompt(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); void submit(); } }} placeholder="Ask Entio…" disabled={Boolean(working)} /><button className="button primary" type="submit" disabled={Boolean(working) || !prompt.trim()}>Send</button></form>
    {error ? <p role="alert" className="ai-error">{error}</p> : null}
    {historyOpen ? <ChatHistory chats={history} activeRunId={run?.runId} onSelect={openChat} onClose={() => setHistoryOpen(false)} /> : null}
    {statusOpen && run ? <StatusDialog updates={Array.isArray(run.updates) ? run.updates : []} onClose={() => setStatusOpen(false)} /> : null}
    {proposalOpen && run ? <ProposalDialog run={{ ...run, edits, validation: run.validation ? { ...run.validation, messages: validationMessages } : null }} onRemove={removeEdit} onStage={stage} onReject={reject} onClose={() => setProposalOpen(false)} /> : null}
  </div>;
}

function ChatHistory({ chats, activeRunId, onSelect, onClose }: { chats: Awaited<ReturnType<typeof listAiChats>>; activeRunId?: string; onSelect: (runId: string) => void; onClose: () => void }) {
  return <div className="ai-modal-backdrop"><section className="ai-modal ai-chat-history" role="dialog" aria-modal="true" aria-labelledby="ai-history-title"><header><h2 id="ai-history-title">Chat history</h2><button className="icon-button" type="button" aria-label="Close chat history" onClick={onClose}>×</button></header><div className="ai-history-list">{chats.length ? chats.map((chat) => <button className={`ai-history-item ${chat.runId === activeRunId ? "active" : ""}`} type="button" key={chat.runId} onClick={() => onSelect(chat.runId)}><strong>{chat.title}</strong><span>{chat.status.toLowerCase()} · {new Date(chat.updatedAt).toLocaleString()}</span></button>) : <p>No previous chats yet.</p>}</div><button className="button" type="button" onClick={onClose}>Close</button></section></div>;
}

function StatusDialog({ updates, onClose }: { updates: WebAiProposalRunResponse["updates"]; onClose: () => void }) {
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { end.current?.scrollIntoView({ block: "nearest" }); }, [updates]);
  return <div className="ai-modal-backdrop"><section className="ai-modal" role="dialog" aria-modal="true" aria-labelledby="ai-status-title"><header><h2 id="ai-status-title">Status Updates</h2><button className="icon-button" type="button" aria-label="Close status updates" onClick={onClose}>×</button></header><div className="ai-status-list">{updates.map((update) => <div className="ai-status-item" key={update.order}><strong>{update.order}</strong><div><span>{update.message}</span>{update.details?.length ? <details className="ai-status-details"><summary>Details</summary><ul>{update.details.map((detail, index) => <li key={`${detail}-${index}`}>{detail}</li>)}</ul></details> : null}</div><time>{new Date(update.timestamp).toLocaleTimeString()}</time></div>)}<div ref={end} /></div><button className="button" type="button" onClick={onClose}>Close</button></section></div>;
}

function ProposalDialog({ run, onRemove, onStage, onReject, onClose }: { run: WebAiProposalRunResponse; onRemove: (id: string) => void; onStage: () => void; onReject: () => void; onClose: () => void }) {
  const edits = Array.isArray(run.edits) ? run.edits : [];
  return <div className="ai-modal-backdrop"><section className="ai-modal ai-proposal-modal" role="dialog" aria-modal="true" aria-labelledby="ai-proposal-title"><header><div><span className="overline">Private proposal</span><h2 id="ai-proposal-title">{run.summary ?? "Review generated edits"}</h2></div><button className="icon-button" type="button" aria-label="Close proposal" onClick={onClose}>×</button></header><div className="ai-edit-list">{edits.map((edit) => <ProposalEdit key={edit.id} edit={edit} onRemove={() => onRemove(edit.id)} />)}</div><div className={`ai-proposal-validation ${run.validation?.valid ? "valid" : "invalid"}`} role="status"><strong>{run.validation?.valid ? "Validation passed" : "Validation requires attention"}</strong>{!run.validation?.valid ? <span>Open Status Updates for the validation details.</span> : null}</div><footer><button className="button danger" type="button" onClick={onReject}>Reject</button><button className="button" type="button" onClick={onClose}>Close</button><button className="button primary" type="button" onClick={onStage} disabled={!run.validation?.valid || run.status !== "READY"}>Stage Changes</button></footer></section></div>;
}

function ProposalEdit({ edit, onRemove }: { edit: WebAiProposalEdit; onRemove: () => void }) {
  return <article className="ai-edit-card"><div><span className="ai-edit-operation">{edit.operation}</span><strong>{edit.summary}</strong></div><code>{edit.subject} — {edit.predicate} — {edit.objectValue}</code>{edit.rationale ? <p>{edit.rationale}</p> : null}<button className="button" type="button" onClick={onRemove}>Remove edit</button></article>;
}
