import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createCollaborationClient, type CollaborationEvent } from "../web/collaboration";
import { queryKeys } from "../web/queries";

export default function CollaborationPresence({ projectId, activeEntityIri }: { projectId: string; activeEntityIri: string | null }) {
  const queryClient = useQueryClient();
  const client = useRef<ReturnType<typeof createCollaborationClient> | null>(null);
  const [users, setUsers] = useState<string[]>([]);
  const [activity, setActivity] = useState("Connecting");

  useEffect(() => {
    if (typeof WebSocket === "undefined") return undefined;
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const collaboration = createCollaborationClient({
      url: `${protocol}//${window.location.host}/api/v1/projects/${encodeURIComponent(projectId)}/collaboration?userId=alice`,
      onEvent: (event) => handleEvent(event),
      onRefresh: () => queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) }),
    });
    client.current = collaboration;
    collaboration.connect();
    return () => { collaboration.close(); client.current = null; };
  }, [projectId, queryClient]);

  useEffect(() => { client.current?.openEntity(activeEntityIri); }, [activeEntityIri]);

  function handleEvent(event: CollaborationEvent) {
    setActivity(event.eventType);
    if (event.eventType === "collaboration.snapshot") {
      const snapshotUsers = event.data?.users;
      if (Array.isArray(snapshotUsers)) setUsers(snapshotUsers.map((user) => typeof user === "object" && user && "id" in user ? String(user.id) : "").filter(Boolean));
    } else if (event.eventType === "presence.joined" && event.userId) {
      setUsers((current) => current.includes(event.userId!) ? current : [...current, event.userId!]);
    } else if (event.eventType === "presence.left" && event.userId) {
      setUsers((current) => current.filter((user) => user !== event.userId));
    }
  }

  return <div className="collaboration-presence" aria-live="polite"><strong>{users.length} connected</strong><span>{activity}</span></div>;
}
