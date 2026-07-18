import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createCollaborationClient, type CollaborationEvent } from "../web/collaboration";
import { queryKeys } from "../web/queries";
import { WEB_DEVELOPMENT_USER_ID } from "../web/session";

export default function CollaborationPresence({ projectId, activeEntityIri }: { projectId: string; activeEntityIri: string | null }) {
  const queryClient = useQueryClient();
  const client = useRef<ReturnType<typeof createCollaborationClient> | null>(null);
  const [users, setUsers] = useState<string[]>([]);
  const [activity, setActivity] = useState("Connecting");
  const [recentEvents, setRecentEvents] = useState<string[]>([]);

  useEffect(() => {
    if (typeof WebSocket === "undefined") return undefined;
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const collaboration = createCollaborationClient({
      url: `${protocol}//${window.location.host}/api/v1/projects/${encodeURIComponent(projectId)}/collaboration?userId=${encodeURIComponent(WEB_DEVELOPMENT_USER_ID)}`,
      onEvent: (event) => handleEvent(event),
      onRefresh: () => queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) }),
    });
    client.current = collaboration;
    collaboration.connect();
    return () => { collaboration.close(); client.current = null; };
  }, [projectId, queryClient]);

  useEffect(() => { client.current?.openEntity(activeEntityIri); }, [activeEntityIri]);

  function handleEvent(event: CollaborationEvent) {
    const description = describeEvent(event);
    setActivity(description);
    setRecentEvents((current) => [description, ...current].slice(0, 5));
    if (event.eventType === "collaboration.snapshot") {
      const snapshotUsers = event.data?.users;
      if (Array.isArray(snapshotUsers)) setUsers(snapshotUsers.map((user) => typeof user === "object" && user && "id" in user ? String(user.id) : "").filter(Boolean));
    } else if (event.eventType === "presence.joined" && event.userId) {
      setUsers((current) => current.includes(event.userId!) ? current : [...current, event.userId!]);
    } else if (event.eventType === "presence.left" && event.userId) {
      setUsers((current) => current.filter((user) => user !== event.userId));
    }
  }

  return <div className="collaboration-presence" aria-live="polite">
    <div className="presence-summary"><strong>{users.length} connected</strong><span role="status">{activity}</span></div>
    {recentEvents.length ? <details className="activity-feed">
      <summary>Recent activity</summary>
      <ol aria-label="Recent collaboration activity">{recentEvents.map((event, index) => <li key={`${event}-${index}`}>{event}</li>)}</ol>
    </details> : null}
  </div>;
}

function describeEvent(event: CollaborationEvent): string {
  if (event.eventType === "presence.joined") return `${event.userId ?? "A user"} joined`;
  if (event.eventType === "presence.left") return `${event.userId ?? "A user"} left`;
  if (event.eventType === "entity.activity") return `${event.userId ?? "A user"} opened an entity`;
  if (event.eventType.startsWith("staged")) return "Staged changes updated";
  if (event.eventType.startsWith("proposal")) return "Proposal activity updated";
  if (event.eventType.startsWith("semantic-job")) return "Semantic job activity updated";
  return event.eventType;
}
