import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import AiTaskWorkspace from "./AiTaskWorkspace";

const task = {
  id: "task-1", conversationId: "conversation-1", projectId: "simple", objective: "Define banking classes",
  type: "MULTI_EDIT_CHANGE", size: "LARGE", status: "EXECUTING", revision: 3, modelId: "gpt-test",
  currentWorkPackageId: "package-a", completedWorkPackageIds: ["package-base"], failedWorkPackageIds: [],
  privateDraftId: "draft-1", createdAt: "2026-07-20T00:00:00Z", updatedAt: "2026-07-20T00:01:00Z",
};

describe("AiTaskWorkspace", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders authoritative package progress and accessible controls without fake percentages", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/events")) return new Response("id: task-1:1\nevent: package-started\ndata: {\"sequence\":1,\"taskId\":\"task-1\",\"type\":\"PACKAGE_STARTED\",\"status\":\"EXECUTING\",\"message\":\"Package started.\",\"referenceIds\":[],\"createdAt\":\"2026-07-20T00:01:00Z\"}\n\n", { status: 200 });
      if (init?.method === "POST") return json({ apiVersion: "v1", task: { ...task, status: "PAUSED", revision: 4 } });
      if (path.endsWith("/workspace")) return json({ apiVersion: "v1", workspace: { task, projectFingerprint: "fingerprint", assumptions: ["Use banking meaning"], openQuestions: [], selectedEntityIris: [], planId: "plan-1", planRevision: 2, analysisReferenceIds: ["validation-1"], repairCycleCount: 1, toolCallCount: 7, pauseCode: null, limits: [] } });
      return json({ apiVersion: "v1", task });
    });
    vi.stubGlobal("fetch", fetcher);
    renderTask();

    expect(await screen.findByRole("heading", { name: "Define banking classes" })).toBeInTheDocument();
    expect(screen.getByText("1 packages complete · 0 blocked")).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    expect(await screen.findByText("PACKAGE STARTED")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Pause" }));
    await waitFor(() => expect(fetcher).toHaveBeenCalledWith(expect.stringContaining("/pause"), expect.objectContaining({ method: "POST" })));
  });

  it("shows review handoff without approval authority", async () => {
    const ready = { ...task, status: "READY_FOR_REVIEW", revision: 8 };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/events")) return new Response("", { status: 200 });
      if (path.endsWith("/workspace")) return json({ apiVersion: "v1", workspace: { task: ready, projectFingerprint: "f", assumptions: [], openQuestions: [], selectedEntityIris: [], planId: null, planRevision: null, analysisReferenceIds: ["final"], repairCycleCount: 0, toolCallCount: 2, pauseCode: null, limits: [] } });
      return json({ apiVersion: "v1", task: ready });
    }));
    renderTask();
    expect(await screen.findByRole("button", { name: "Submit for human review" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /approve|apply/i })).not.toBeInTheDocument();
  });
});

function renderTask() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><AiTaskWorkspace projectId="simple" taskId="task-1" /></QueryClientProvider>);
}

function json(value: unknown) {
  return Promise.resolve(new Response(JSON.stringify(value), { status: 200, headers: { "Content-Type": "application/json" } }));
}
