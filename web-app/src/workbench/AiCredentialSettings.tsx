import { useState } from "react";
import { useAiProviderActions, useAiProviderSettings } from "../web/queries";
import type { WebAiProviderSettings } from "../web/contracts";

export default function AiCredentialSettings() {
  const settings = useAiProviderSettings();
  const actions = useAiProviderActions();
  const [apiKey, setApiKey] = useState("");
  const current = settings.data;
  const configured = Boolean(current && current.credentialStatus !== "NOT_CONFIGURED");
  const busy = actions.save.isPending || actions.discover.isPending || actions.select.isPending || actions.retest.isPending || actions.clear.isPending || actions.remove.isPending;

  function save() {
    actions.save.mutate({ providerId: "openai", apiKey }, { onSettled: () => setApiKey("") });
  }

  return (
    <section className="content-band ai-credential-settings" aria-labelledby="ai-credentials-heading">
      <div className="section-heading">
        <div><p className="eyebrow">Optional native AI</p><h2 id="ai-credentials-heading">OpenAI provider</h2></div>
        <span className={`ai-state ${current?.selectionStatus === "READY" ? "ai-state-ready" : "ai-state-unavailable"}`} role="status" aria-live="polite">
          {settings.isPending ? "Loading" : actions.select.isPending || actions.retest.isPending ? "Verifying model" : actions.discover.isPending ? "Refreshing models" : statusLabel(current)}
        </span>
      </div>
      <p className="muted">Your key stays in server memory and is cleared from this form after submission. Entio returns only curated model descriptors.</p>
      <label htmlFor="ai-provider-id">Provider</label>
      <input id="ai-provider-id" value="OpenAI" readOnly aria-describedby="ai-provider-description" />
      <small id="ai-provider-description">The server owns provider endpoints, compatibility rules, and request settings.</small>
      <label htmlFor="ai-api-key">Credential</label>
      <input id="ai-api-key" type="password" autoComplete="off" value={apiKey} onChange={(event) => setApiKey(event.target.value)} placeholder={configured ? "Enter a replacement key" : "Enter an OpenAI API key"} />
      <div className="button-row">
        <button className="button primary" type="button" onClick={save} disabled={actions.save.isPending || !apiKey.trim()}>{configured ? "Replace key" : "Save credential"}</button>
        <button className="button" type="button" onClick={() => actions.discover.mutate()} disabled={busy || !configured}>Refresh models</button>
        <button className="button danger" type="button" onClick={() => actions.remove.mutate()} disabled={busy || !configured}>Remove key</button>
      </div>

      {current ? <ProviderState settings={current} /> : null}

      {current?.models?.length ? (
        <fieldset className="ai-model-selector" disabled={busy}>
          <legend>Available models</legend>
          <p className="muted">Select and test is required even when one model is available. Verification sends a harmless request and may incur a small API charge.</p>
          <div className="ai-model-list">
            {current.models.map((model) => (
              <article className={`ai-model-card ${current.selectedModel?.modelId === model.modelId ? "selected" : ""}`} key={model.modelId}>
                <div><strong>{model.displayName}</strong>{model.recommended ? <span className="badge">Recommended</span> : null}</div>
                <code>{model.modelId}</code>
                <p>{model.description}</p>
                <small>{[model.capabilityTier?.toLowerCase(), model.relativeSpeed, model.relativeCost].filter(Boolean).join(" · ")}</small>
                <button className="button" type="button" onClick={() => actions.select.mutate({ modelId: model.modelId, idempotencyKey: requestId("select") })}>
                  {current.selectedModel?.modelId === model.modelId && current.selectionStatus === "READY" ? "Selected and verified" : `Select and test ${model.displayName}`}
                </button>
              </article>
            ))}
          </div>
        </fieldset>
      ) : null}

      {current?.selectionStatus === "READY" ? <div className="button-row">
        <button className="button" type="button" onClick={() => actions.retest.mutate(requestId("retest"))} disabled={busy}>Retest selected model</button>
        <button className="button" type="button" onClick={() => actions.clear.mutate()} disabled={busy}>Clear selection</button>
      </div> : null}

      {actions.save.isError ? <p role="alert">Could not save the key. {actions.save.error.message}</p> : null}
      {actions.discover.isError ? <p role="alert">Could not refresh models. {actions.discover.error.message}</p> : null}
      {actions.select.isError ? <p role="alert">Could not verify that model. {actions.select.error.message}</p> : null}
      {actions.retest.isError ? <p role="alert">Could not retest the selected model. {actions.retest.error.message}</p> : null}
      {actions.clear.isError ? <p role="alert">Could not clear the selection. {actions.clear.error.message}</p> : null}
      {actions.remove.isError ? <p role="alert">Could not remove the key. {actions.remove.error.message}</p> : null}
      {actions.remove.isSuccess ? <p role="status">Credential removed.</p> : null}
    </section>
  );
}

function ProviderState({ settings }: { settings: WebAiProviderSettings }) {
  if (settings.credentialStatus === "NOT_CONFIGURED") return <p role="status">Add an OpenAI API key to discover compatible models.</p>;
  if (settings.credentialStatus === "INVALID") return <p role="alert">The OpenAI credential is invalid. Replace or remove the key.</p>;
  if (settings.errorCode === "AI_PROVIDER_RATE_LIMITED") return <p role="alert">OpenAI rate-limited this request. Wait before refreshing or testing again.</p>;
  if (settings.errorCode === "AI_PROVIDER_TIMEOUT") return <p role="alert">OpenAI did not respond in time. Refresh models or retest when the service recovers.</p>;
  if (settings.errorCode === "AI_PROVIDER_UNAVAILABLE") return <p role="alert">OpenAI is currently unavailable. Your credential and model choice were not exposed or replaced.</p>;
  if (settings.discoveryStatus === "STALE") return <p role="status">The available-model list is stale. Refresh models before selecting or testing.</p>;
  if (settings.discoveryStatus === "NO_COMPATIBLE_MODELS") return <div role="status"><strong>No compatible models</strong><p>This API project does not currently expose a model supported by Entio. Refresh models or replace the key; non-AI features remain available.</p></div>;
  if (settings.discoveryStatus === "FAILED") return <p role="alert">Model discovery failed safely ({settings.errorCode ?? "AI_MODEL_DISCOVERY_FAILED"}). Refresh models or replace the key.</p>;
  if (settings.selectionStatus === "UNAVAILABLE" || settings.selectionStatus === "INCOMPATIBLE") return <p role="alert">The selected model is no longer available. Refresh models and explicitly select another model.</p>;
  if (settings.selectionStatus === "VERIFICATION_FAILED") return <p role="alert">Model verification failed. Your credential remains configured; select a model and try again.</p>;
  if (settings.selectionStatus === "READY") return <p role="status">Ready with {settings.selectedModel?.displayName ?? settings.selectedModel?.modelId}.</p>;
  if (settings.models?.length) return <p role="status">Credential valid. Select and test a model to enable the assistant.</p>;
  return <p role="status">Discovering available models…</p>;
}

function statusLabel(settings?: WebAiProviderSettings): string {
  if (!settings || settings.credentialStatus === "NOT_CONFIGURED") return "Not configured";
  if (settings.credentialStatus === "INVALID") return "Invalid credential";
  if (settings.discoveryStatus === "NO_COMPATIBLE_MODELS") return "No compatible models";
  if (settings.selectionStatus === "READY") return "AI ready";
  if (settings.selectionStatus === "UNAVAILABLE" || settings.selectionStatus === "INCOMPATIBLE") return "Model unavailable";
  if (settings.selectionStatus === "VERIFICATION_FAILED") return "Verification failed";
  return "Model selection required";
}

function requestId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
