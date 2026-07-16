export interface CollaborationEvent {
  eventId: string;
  projectId: string;
  collaborationSessionId: string;
  sequence: number;
  eventType: string;
  timestamp: string;
  userId?: string | null;
  entityIri?: string | null;
  stagedChangeId?: string | null;
  proposalId?: string | null;
  jobId?: string | null;
  data?: Record<string, unknown>;
}

export interface CollaborationSocket {
  readyState: number;
  onopen: (() => void) | null;
  onmessage: ((event: MessageEvent<string>) => void) | null;
  onclose: (() => void) | null;
  onerror: (() => void) | null;
  send(data: string): void;
  close(): void;
}

export type CollaborationSocketFactory = (url: string) => CollaborationSocket;

export interface CollaborationClientOptions {
  url: string;
  onEvent: (event: CollaborationEvent) => void;
  onRefresh: () => void;
  socketFactory?: CollaborationSocketFactory;
  reconnectDelayMs?: number;
}

/** Keeps transport ordering separate from authoritative HTTP state. */
export function createCollaborationClient(options: CollaborationClientOptions) {
  const socketFactory = options.socketFactory ?? ((url: string) => new WebSocket(url) as unknown as CollaborationSocket);
  const seenEventIds = new Set<string>();
  let socket: CollaborationSocket | null = null;
  let stopped = false;
  let lastSequence = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

  function connect() {
    stopped = false;
    const currentSocket = socketFactory(options.url);
    socket = currentSocket;
    currentSocket.onopen = () => undefined;
    currentSocket.onmessage = (message) => {
      let event: CollaborationEvent;
      try {
        event = JSON.parse(message.data) as CollaborationEvent;
      } catch {
        options.onRefresh();
        return;
      }
      if (seenEventIds.has(event.eventId) || event.sequence <= lastSequence) return;
      seenEventIds.add(event.eventId);
      if (event.sequence !== lastSequence + 1) {
        lastSequence = event.sequence;
        options.onRefresh();
        return;
      }
      lastSequence = event.sequence;
      options.onEvent(event);
    };
    currentSocket.onerror = () => options.onRefresh();
    currentSocket.onclose = () => {
      socket = null;
      if (!stopped) reconnectTimer = setTimeout(connect, options.reconnectDelayMs ?? 500);
    };
  }

  function close() {
    stopped = true;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    socket?.close();
    socket = null;
  }

  function openEntity(entityIri: string | null) {
    if (socket?.readyState === 1) socket.send(JSON.stringify({ type: entityIri ? "entity-opened" : "entity-closed", entityIri }));
  }

  return { connect, close, openEntity };
}
