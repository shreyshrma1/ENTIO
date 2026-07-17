import type { WebAiRunEvent } from "../../web/contracts";

export type AiStreamState = "idle" | "connecting" | "connected" | "reconnecting" | "disconnected";

export default function AiRunTimeline({ events, streamState }: { events: WebAiRunEvent[]; streamState: AiStreamState }) {
  if (!events.length && streamState === "idle") return null;
  return (
    <section className="ai-activity" aria-labelledby="ai-activity-heading">
      <div className="ai-subheading">
        <h3 id="ai-activity-heading">Capability activity</h3>
        <span className={`ai-connection ai-connection-${streamState}`} role="status">{connectionLabel(streamState)}</span>
      </div>
      {events.length ? <ol className="ai-timeline">
        {events.map((event) => <li key={`${event.runId}:${event.sequence}`}>
          <span className="ai-timeline-marker" aria-hidden="true" />
          <div><strong>{eventLabel(event.type)}</strong><p>{event.message}</p>{event.referenceIds.length ? <details><summary>Evidence and provenance</summary><ul>{event.referenceIds.map((reference) => <li key={reference}>{reference}</li>)}</ul></details> : null}</div>
        </li>)}
      </ol> : <p className="muted">Waiting for safe run activity.</p>}
    </section>
  );
}

function connectionLabel(state: AiStreamState): string {
  if (state === "connecting") return "Connecting";
  if (state === "connected") return "Connected";
  if (state === "reconnecting") return "Reconnecting";
  if (state === "disconnected") return "Disconnected";
  return "Idle";
}

function eventLabel(type: string): string {
  return type.toLowerCase().split("_").map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(" ");
}
