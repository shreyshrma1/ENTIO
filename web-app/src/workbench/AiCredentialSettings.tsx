import { useState } from "react";
import { useAiCredentialActions, useAiCredentialStatus } from "../web/queries";

export default function AiCredentialSettings() {
  const status = useAiCredentialStatus();
  const actions = useAiCredentialActions();
  const [providerId, setProviderId] = useState("provider-neutral");
  const [apiKey, setApiKey] = useState("");

  function save() {
    actions.save.mutate({ providerId, apiKey }, { onSuccess: () => setApiKey("") });
  }

  return (
    <section className="content-band ai-credential-settings" aria-labelledby="ai-credentials-heading">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Optional native AI</p>
          <h2 id="ai-credentials-heading">Provider credential</h2>
        </div>
        <span>{status.data?.testStatus ?? "Loading"}</span>
      </div>
      <p className="muted">Credentials stay in server memory and are never returned to the browser after submission.</p>
      <label htmlFor="ai-provider-id">Provider ID</label>
      <input id="ai-provider-id" value={providerId} onChange={(event) => setProviderId(event.target.value)} />
      <label htmlFor="ai-api-key">Credential</label>
      <input id="ai-api-key" type="password" autoComplete="off" value={apiKey} onChange={(event) => setApiKey(event.target.value)} placeholder="Enter a provider credential" />
      <div className="button-row">
        <button type="button" onClick={save} disabled={actions.save.isPending || !apiKey.trim()}>Save credential</button>
        <button type="button" onClick={() => actions.test.mutate()} disabled={actions.test.isPending || !status.data?.configured}>Test credential</button>
        <button type="button" onClick={() => actions.remove.mutate()} disabled={actions.remove.isPending || !status.data?.configured}>Remove credential</button>
      </div>
      {actions.save.isError ? <p role="alert">Could not save credential. {actions.save.error.message}</p> : null}
      {actions.test.isError ? <p role="alert">Could not test credential. {actions.test.error.message}</p> : null}
      {actions.test.data ? <p role={actions.test.data.status === "PASSED" ? "status" : "alert"}>{actions.test.data.message}</p> : null}
      {actions.remove.isSuccess ? <p role="status">Credential removed.</p> : null}
    </section>
  );
}
