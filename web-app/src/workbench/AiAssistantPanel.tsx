import { FormEvent, useState } from "react";
import { useAiAssistant, useStagingActions } from "../web/queries";
import type { WebAiOperation, WebEntityReference } from "../web/projectApi";

const operations: WebAiOperation[] = [
  "EXPLAIN_ENTITY",
  "EXPLAIN_INFERENCE",
  "EXPLAIN_SHACL_RESULT",
  "SEARCH_FIBO",
  "SUGGEST_DEFINITION",
  "SUGGEST_SUPERCLASS",
  "SUGGEST_PROPERTY",
  "SUGGEST_EXTERNAL_REUSE",
  "SUMMARIZE_PROPOSAL",
];

export default function AiAssistantPanel({ projectId, entity }: { projectId: string; entity?: WebEntityReference | null }) {
  const assistant = useAiAssistant(projectId);
  const staging = useStagingActions(projectId);
  const [operation, setOperation] = useState<WebAiOperation>("EXPLAIN_ENTITY");
  const [question, setQuestion] = useState("");
  const [stagedSuggestion, setStagedSuggestion] = useState<string | null>(null);

  function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStagedSuggestion(null);
    assistant.mutate({
      operation,
      entityIri: entity?.iri,
      question: question.trim() || undefined,
    });
  }

  return (
    <section className="content-band ai-assistant-panel" aria-labelledby="ai-assistant-heading">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Bounded semantic help</p>
          <h2 id="ai-assistant-heading">Assistant</h2>
        </div>
        {entity ? <span className="status-text">Context: {entity.label}</span> : null}
      </div>
      <p className="muted">The assistant receives only selected semantic context. Suggestions become staged edits only after you choose to stage them.</p>
      <form className="ai-assistant-form" onSubmit={ask}>
        <label htmlFor="ai-operation">Operation</label>
        <select id="ai-operation" value={operation} onChange={(event) => setOperation(event.target.value as WebAiOperation)}>
          {operations.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
        <label htmlFor="ai-question">Request or IRI</label>
        <input id="ai-question" value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Ask about this entity or provide a typed IRI" />
        <button className="button primary" type="submit" disabled={assistant.isPending}>Ask assistant</button>
      </form>
      {assistant.isPending ? <p role="status">Consulting the bounded assistant...</p> : null}
      {assistant.isError ? <p role="alert">Assistant unavailable. {assistant.error.message}</p> : null}
      {assistant.data ? <AssistantResponse response={assistant.data} onStage={(suggestion) => {
        staging.stage.mutate(
          { ...suggestion.edit, aiGenerated: true, idempotencyKey: `ai-${suggestion.id}-${Date.now()}` },
          { onSuccess: () => setStagedSuggestion(suggestion.id) },
        );
      }} stagedSuggestion={stagedSuggestion} stagingPending={staging.stage.isPending} /> : null}
    </section>
  );
}

function AssistantResponse({
  response,
  onStage,
  stagedSuggestion,
  stagingPending,
}: {
  response: Awaited<ReturnType<typeof useAiAssistant>>["data"];
  onStage: (suggestion: NonNullable<typeof response>["suggestions"][number]) => void;
  stagedSuggestion: string | null;
  stagingPending: boolean;
}) {
  if (!response) return null;
  return (
    <div className="assistant-response">
      <p>{response.answer}</p>
      <AssistantList heading="Evidence" items={response.evidence.map((item) => `${item.label}: ${item.value}`)} />
      <AssistantList heading="Asserted facts" items={response.assertedFacts} />
      <AssistantList heading="Inferred facts" items={response.inferredFacts} />
      <AssistantList heading="FIBO results" items={response.fiboResults.map((item) => `${item.label}: ${item.value}`)} />
      {response.suggestions.length ? <section className="assistant-section" aria-labelledby="assistant-suggestions-heading">
        <h3 id="assistant-suggestions-heading">Typed suggestions</h3>
        <ul className="assistant-list">
          {response.suggestions.map((suggestion) => <li key={suggestion.id}><strong>{suggestion.suggestionType}</strong><span>{suggestion.rationale}</span><button className="button primary small" type="button" onClick={() => onStage(suggestion)} disabled={stagingPending || stagedSuggestion === suggestion.id}>{stagedSuggestion === suggestion.id ? "Staged for review" : "Stage suggestion"}</button></li>)}
        </ul>
      </section> : null}
      <AssistantList heading="Uncertainty" items={response.uncertainty} />
      <AssistantList heading="Warnings" items={response.warnings} />
    </div>
  );
}

function AssistantList({ heading, items }: { heading: string; items: string[] }) {
  if (!items.length) return null;
  return <section className="assistant-section" aria-labelledby={`assistant-${heading.toLowerCase().replaceAll(" ", "-")}`}><h3 id={`assistant-${heading.toLowerCase().replaceAll(" ", "-")}`}>{heading}</h3><ul className="assistant-list">{items.map((item, index) => <li key={`${heading}-${index}`}>{item}</li>)}</ul></section>;
}
