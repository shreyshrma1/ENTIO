import { describe, expect, it } from "vitest";
import { createCollaborationClient, type CollaborationSocket } from "./collaboration";

class FakeSocket implements CollaborationSocket {
  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  sent: string[] = [];
  send(data: string) { this.sent.push(data); }
  close() { this.readyState = 3; this.onclose?.(); }
  open() { this.readyState = 1; this.onopen?.(); }
  message(event: unknown) { this.onmessage?.({ data: JSON.stringify(event) } as MessageEvent<string>); }
}

function event(sequence: number, eventId = `event-${sequence}`) {
  return { eventId, projectId: "simple", collaborationSessionId: "collaboration-simple", sequence, eventType: "presence.joined", timestamp: "2026-01-01T00:00:00Z" };
}

describe("collaboration transport ordering", () => {
  it("ignores duplicate events and refreshes on sequence gaps", () => {
    const socket = new FakeSocket();
    const received: number[] = [];
    let refreshes = 0;
    const client = createCollaborationClient({ url: "/collaboration", socketFactory: () => socket, onEvent: (item) => received.push(item.sequence), onRefresh: () => refreshes++ });
    client.connect();
    socket.open();
    socket.message(event(1));
    socket.message(event(1));
    socket.message(event(3));

    expect(received).toEqual([1]);
    expect(refreshes).toBe(1);
    client.close();
  });

  it("sends entity activity only through the collaboration socket", () => {
    const socket = new FakeSocket();
    const client = createCollaborationClient({ url: "/collaboration", socketFactory: () => socket, onEvent: () => undefined, onRefresh: () => undefined });
    client.connect();
    socket.open();
    client.openEntity("https://example.com/Customer");
    expect(socket.sent).toEqual([JSON.stringify({ type: "entity-opened", entityIri: "https://example.com/Customer" })]);
    client.close();
  });
});
