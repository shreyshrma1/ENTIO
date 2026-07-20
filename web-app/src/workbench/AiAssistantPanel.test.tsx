import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import AiAssistantPanel from "./AiAssistantPanel";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("AI assistant panel", () => {
  it("renders conversation history, sends a follow-up, and orders safe run activity", async () => {
    const initial = conversation([message("message-1", "ASSISTANT", "Customer is an asserted class.")]);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/messages") && init?.method === "POST") return json(turn({
        messages: [...initial.messages, { ...message("message-2", "USER", "What uses it?"), operation: null }, message("message-3", "ASSISTANT", "Invoice uses Customer.")],
        answer: "Invoice uses Customer.",
      }));
      if (path.endsWith("/events")) return eventStream([
        event("run-1", 2, "TEXT_COMPLETED", "Answer completed."),
        event("run-1", 1, "RUN_STARTED", "Run started."),
      ]);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    expect(await screen.findByText("Customer is an asserted class.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "What uses it?" } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));

    expect(await screen.findByText("Invoice uses Customer.")).toBeInTheDocument();
    expect(await screen.findByText("Run started.")).toBeInTheDocument();
    const activity = screen.getByRole("region", { name: "Capability activity" }).querySelectorAll("li");
    expect(activity[0]).toHaveTextContent("Run started");
    expect(activity[1]).toHaveTextContent("Answer completed");
  });

  it("shows a working status while a longer assistant request is in progress", async () => {
    const initial = conversation([]);
    let completeRequest: ((response: Response) => void) | undefined;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/messages") && init?.method === "POST") {
        return new Promise<Response>((resolve) => { completeRequest = resolve; });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    await screen.findByText("Ready when you are");
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "Define every class." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));

    expect(await screen.findByText("Entio AI is working…")).toBeInTheDocument();
    expect(screen.getByText(/waiting briefly for provider capacity/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Stop generation" })).toBeInTheDocument();
    completeRequest?.(json(turn({ answer: "Drafted definitions." })));
  });

  it("lets the user stop an in-flight generation request", async () => {
    const initial = conversation([]);
    let requestSignal: AbortSignal | undefined;
    let aborted = false;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/messages") && init?.method === "POST") {
        requestSignal = init.signal ?? undefined;
        return new Promise<Response>((_resolve, reject) => {
          init.signal?.addEventListener("abort", () => {
            aborted = true;
            reject(new DOMException("Aborted", "AbortError"));
          }, { once: true });
        });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    await screen.findByText("Ready when you are");
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "Model a long-running loan workflow." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    fireEvent.click(await screen.findByRole("button", { name: "Stop generation" }));

    await waitFor(() => expect(aborted).toBe(true));
    expect(requestSignal?.aborted).toBe(true);
    expect(screen.queryByText(/Assistant request failed/i)).not.toBeInTheDocument();
  });

  it("supports plan confirmation and sends the explicit decision", async () => {
    const decisions: string[] = [];
    const initial = conversation([]);
    let messageCall = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/messages") && init?.method === "POST") {
        const body = JSON.parse(String(init.body)) as { decision: string };
        decisions.push(body.decision);
        messageCall += 1;
        if (messageCall === 1) return json(turn({
          status: "AWAITING_PLAN_CONFIRMATION",
          plan: { request: "Design accounting concepts.", steps: ["Inspect existing classes", "Prepare typed edits"], openDecisions: [], estimatedEditCount: 2 },
          answer: "Please confirm the plan.",
        }));
        return json(turn({ answer: "The confirmed plan is complete.", messages: [message("message-4", "ASSISTANT", "The confirmed plan is complete.")] }));
      }
      if (path.endsWith("/events")) return eventStream([event("run-1", messageCall, "STATUS_CHANGED", "Status changed.")]);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    await screen.findByText("Ready when you are");
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "Design accounting concepts." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByRole("heading", { name: "Confirm the plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Confirm plan" }));
    expect(await screen.findByText("The confirmed plan is complete.")).toBeInTheDocument();
    expect(decisions).toEqual(["MESSAGE", "CONFIRM_PLAN"]);
  });

  it("asks for clarification and preserves the explicit answer decision", async () => {
    const initial = conversation([]);
    const decisions: string[] = [];
    let messageCall = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/messages")) {
        const body = JSON.parse(String(init?.body)) as { decision: string };
        decisions.push(body.decision);
        messageCall += 1;
        const response = turn({ status: messageCall === 1 ? "AWAITING_CLARIFICATION" : "READY_FOR_REVIEW", answer: messageCall === 1 ? "I need one detail." : "Thanks, the class is local." });
        return json({ ...response, clarificationQuestion: messageCall === 1 ? "Should this be a local class?" : null });
      }
      if (path.endsWith("/events")) return eventStream([event("run-1", messageCall, "STATUS_CHANGED", "Status changed.")]);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    await screen.findByText("Ready when you are");
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "Create an account concept." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByText("Should this be a local class?")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Your answer"), { target: { value: "Yes, local." } });
    fireEvent.click(screen.getByRole("button", { name: "Answer clarification" }));
    expect(await screen.findByText("Thanks, the class is local.")).toBeInTheDocument();
    expect(decisions).toEqual(["MESSAGE", "ANSWER_CLARIFICATION"]);
  });

  it("shows conflicted drafts and submits analyzed drafts to review without applying", async () => {
    const paths: string[] = [];
    const initial = conversation([], "draft-1");
    let conflicted = true;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      paths.push(`${init?.method ?? "GET"} ${path}`);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/ai/drafts/draft-1") && !init?.method) return json({ apiVersion: "v1", draft: draft(conflicted ? "CONFLICTED" : "READY_FOR_REVIEW") });
      if (path.endsWith("/analysis") && init?.method === "POST") {
        conflicted = false;
        return json({ apiVersion: "v1", analysis: analysis() });
      }
      if (path.endsWith("/submit") && init?.method === "POST") return json({
        apiVersion: "v1", submissionId: "submission-1", proposalId: "proposal-1", reviewState: "READYFORREVIEW", projectId: "simple", draftId: "draft-1", draftRevision: 1, submittingUserId: "alice", conversationId: "conversation-1", runId: "run-1", rationale: "review", diff: [], analysisReferenceIds: ["analysis-ref"], reviewRoute: "/projects/simple/changes?proposalId=proposal-1",
      });
      throw new Error(`Unexpected request: ${path}`);
    }));

    const client = renderPanel();
    expect(await screen.findByText(/This draft is conflicted/i)).toBeInTheDocument();
    expect(screen.getByText("Proposed definition")).toBeInTheDocument();
    expect(screen.getByText("A financial record of a customer's balance and transactions.")).toBeInTheDocument();
    expect(screen.getByText("Rationale")).toBeInTheDocument();
    expect(screen.getByText("Use an entity-centered financial definition.")).toBeInTheDocument();
    conflicted = false;
    client.invalidateQueries({ queryKey: queryKey("draft") });
    await waitFor(() => expect(screen.queryByText(/This draft is conflicted/i)).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Run deterministic analysis" }));
    expect(await screen.findByText(/Ready for human review/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Submit for human review" }));
    const review = await screen.findByRole("link", { name: "Open proposal review" });
    expect(review).toHaveAttribute("href", "/projects/simple/changes?proposalId=proposal-1");
    expect(screen.queryByRole("heading", { name: "Private draft" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Deterministic analysis" })).not.toBeInTheDocument();
    expect(paths.some((path) => path.includes("/apply"))).toBe(false);
  });

  it("recovers authoritative conversation state after an event resynchronization signal", async () => {
    const initial = conversation([]);
    let conversationLoads = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) { conversationLoads += 1; return json({ apiVersion: "v1", conversation: initial }); }
      if (path.endsWith("/messages")) return json(turn({ answer: "Recovered answer." }));
      if (path.endsWith("/events")) return sse("event: resynchronization-required\ndata: {\"apiVersion\":\"v1\",\"runId\":\"run-1\",\"reason\":\"cursor expired\",\"authoritativeRunRoute\":\"/run\",\"authoritativeConversationRoute\":\"/conversation\"}\n\n");
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    await screen.findByText("Ready when you are");
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "Explain Customer." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));
    await waitFor(() => expect(conversationLoads).toBeGreaterThan(1));
  });

  it("keeps the non-AI workbench available when no credential is configured", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(false));
      if (path.endsWith("/ai/conversations")) return json({ apiVersion: "v1", conversations: [] });
      throw new Error(`Unexpected request: ${path}`);
    }));
    renderPanel();
    expect(await screen.findByText(/Add an OpenAI credential/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New conversation" })).toBeDisabled();
    expect(screen.getByText(/rest of the workbench remains available/i)).toBeInTheDocument();
  });

  it("requires a verified model when a credential is configured", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true, false));
      if (path.endsWith("/ai/conversations")) return json({ apiVersion: "v1", conversations: [] });
      throw new Error(`Unexpected request: ${path}`);
    }));
    renderPanel();
    expect(await screen.findByText(/Select and verify an available model/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New conversation" })).toBeDisabled();
    expect(screen.queryByLabelText("Ask about this ontology context")).not.toBeInTheDocument();
  });

  it("renders a failed provider run as an error state without review or apply controls", async () => {
    const initial = conversation([]);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings")) return json(providerSettings(true));
      if (path.endsWith("/ai/conversations") && !init?.method) return json({ apiVersion: "v1", conversations: [initial] });
      if (path.endsWith("/ai/conversations/conversation-1") && !init?.method) return json({ apiVersion: "v1", conversation: initial });
      if (path.endsWith("/messages") && init?.method === "POST") return json(turn({ status: "FAILED", answer: "The provider timed out; no ontology source changed." }));
      if (path.endsWith("/events")) return eventStream([event("run-1", 1, "FAILED", "The provider request failed safely.")]);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    await screen.findByText("Ready when you are");
    fireEvent.change(screen.getByLabelText("Ask about this ontology context"), { target: { value: "Explain Customer." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));

    expect(await screen.findByText("FAILED")).toBeInTheDocument();
    expect(await screen.findByText(/no ontology source changed/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Submit for human review/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Apply/i })).not.toBeInTheDocument();
  });
});

function renderPanel(): QueryClient {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 } } });
  render(<QueryClientProvider client={client}><AiAssistantPanel projectId="simple" entity={{ iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" }} /></QueryClientProvider>);
  return client;
}

function queryKey(resource: string): string[] {
  return ["project", "simple", "ai", resource, resource === "draft" ? "draft-1" : "conversation-1"];
}

function providerSettings(configured: boolean, ready = configured) {
  const model = { providerId: "openai", modelId: "gpt-5.2", displayName: "GPT-5.2", family: "GPT-5", lifecycle: "CURRENT", supported: true, supportReason: "Supported", capabilities: ["RESPONSES", "TOOLS"], sortOrder: 10 };
  return { apiVersion: "v1", providerId: "openai", credentialStatus: configured ? "VALID" : "NOT_CONFIGURED", discoveryStatus: configured ? "COMPLETED" : "NOT_REQUESTED", discoveredAt: configured ? "2026-07-17T12:00:00Z" : null, policyVersion: "phase-7.5-compatibility-v1", models: configured ? [model] : [], unsupportedProviderModelCount: 0, selectedModel: ready ? model : null, selectionStatus: ready ? "READY" : configured ? "NOT_SELECTED" : "NOT_CONFIGURED", selectedModelVerifiedAt: ready ? "2026-07-17T12:00:00Z" : null, errorCode: null, availableActions: [] };
}

function message(id: string, role: "USER" | "ASSISTANT" | "TOOL", content: string) { return { id, role, content, operation: null, evidenceReferenceIds: [], createdAt: "2026-07-17T12:00:00Z" }; }

function conversation(messages: ReturnType<typeof message>[], currentDraftId: string | null = null) {
  return { id: "conversation-1", projectId: "simple", messages, currentDraftId, modelId: "gpt-5.2", status: "ACTIVE", createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" };
}

function turn(overrides: { status?: string; answer?: string; plan?: unknown; messages?: ReturnType<typeof message>[] } = {}) {
  return { apiVersion: "v1", conversation: conversation(overrides.messages ?? [message("message-answer", "ASSISTANT", overrides.answer ?? "Answer")]), run: { id: "run-1", conversationId: "conversation-1", projectId: "simple", status: overrides.status ?? "READY_FOR_REVIEW", capabilityCallCount: 1, draftEditCount: 0, correctionCycleCount: 0, cancellationRequested: false, createdAt: "2026-07-17T12:00:00Z", updatedAt: `${Date.now()}` }, intent: "EXPLANATION", answer: overrides.answer ?? "Answer", plan: overrides.plan ?? null, clarificationQuestion: null, draftId: null, limits: [] };
}

function draft(status: string) { return { id: "draft-1", conversationId: "conversation-1", projectId: "simple", baselineFingerprint: "baseline", allowedSourceIds: ["simple"], status, draftFingerprint: "draft-fingerprint", analysisReferenceIds: [], items: [{ id: "item-1", order: 1, capabilityName: "entio_draft_add_definition", targetSourceId: "simple", summary: "add-definition · Account", editType: "add-definition", targetLabel: "Account", targetIri: "https://example.com/entio/simple#Account", value: "A financial record of a customer's balance and transactions.", rationale: "Use an entity-centered financial definition.", dependencyItemIds: [], aiGenerated: true, acceptingUserId: "alice", runId: "run-1" }], revisions: [{ revision: 1, action: "ADD", explanation: "Created the draft.", itemIds: ["item-1"], undoneRevision: null, createdAt: "2026-07-17T12:00:00Z" }], createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" }; }

function analysis() { return { id: "analysis-1", draftId: "draft-1", revision: 1, status: "COMPLETED", baselineFingerprint: "baseline", draftFingerprint: "draft-fingerprint", previewGraphFingerprint: "preview", readyForReview: true, validationOk: true, findings: [], diff: [{ kind: "ADDED", subject: "ReceivableAccount", predicate: "type", objectValue: "Class", description: "Added class Receivable Account." }], references: [{ stage: "VALIDATION", id: "analysis-ref" }], createdAt: "2026-07-17T12:00:00Z" }; }

function event(runId: string, sequence: number, type: string, content: string) { return { sequence, runId, type, message: content, referenceIds: [], createdAt: "2026-07-17T12:00:00Z" }; }

function eventStream(events: ReturnType<typeof event>[]): Response { return sse(events.map((item) => `id: ${item.runId}:${item.sequence}\nevent: ${item.type.toLowerCase().replaceAll("_", "-")}\ndata: ${JSON.stringify(item)}\n\n`).join("")); }

function sse(body: string): Response { return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } }); }
function json(body: unknown): Response { return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } }); }
