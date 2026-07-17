import { expect, test } from "@playwright/test";

test("completes the browser workbench journey through reviewable staging", async ({ page }) => {
  let stagedEditType: string | undefined;
  let aiMessageCall = 0;
  let aiDraftAnalyzed = false;
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === "/api/v1/projects" && request.method() === "GET") return json(route, { apiVersion: "v1", projects: [{ id: "simple", displayName: "Simple ontology" }] });
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 7 }],
      symbolCount: 5,
      graphTripleCount: 7,
    });
    if (path.endsWith("/hierarchy")) return json(route, {
      apiVersion: "v1", sourceId: "simple", parentIri: null,
      page: { items: [{ iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple", childCount: 0 }], offset: 0, limit: 50, total: 1, nextOffset: null },
    });
    if (path.endsWith("/outline")) return json(route, {
      apiVersion: "v1", sourceId: "simple",
      page: { items: [
        { iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple" },
        { iri: "https://example.com/entio/simple#Shrey", label: "Shrey", kind: "Individual", sourceId: "simple" },
        { iri: "https://example.com/entio/simple#receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple" },
      ], offset: 0, limit: 100, total: 3, nextOffset: null },
    });
    if (path.endsWith("/entities")) return json(route, {
      apiVersion: "v1", iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel",
      alternateLabels: [], definitions: [{ value: "A customer.", language: null, datatype: null }], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [], outgoingRelationships: [], incomingRelationships: [],
    });
    if (path.endsWith("/ai/credential-status")) return json(route, { apiVersion: "v1", configured: true, providerId: "openai", testStatus: "PASSED" });
    if (path.endsWith("/ai/conversations") && request.method() === "GET") return json(route, { apiVersion: "v1", conversations: [aiConversation([])] });
    if (path.endsWith("/ai/conversations/conversation-1") && request.method() === "GET") return json(route, { apiVersion: "v1", conversation: aiConversation([]) });
    if (path.endsWith("/ai/conversations/conversation-1/messages") && request.method() === "POST") {
      aiMessageCall += 1;
      if (aiMessageCall === 1) return json(route, aiTurn({
        runId: "run-1",
        answer: "Customer is an asserted class in the selected project.",
        messages: [
          aiMessage("message-1", "USER", "Explain Customer."),
          aiMessage("message-2", "ASSISTANT", "Customer is an asserted class in the selected project.", "EXPLAIN_ENTITY", ["entity:Customer"]),
        ],
      }));
      if (aiMessageCall === 2) return json(route, aiTurn({
        runId: "run-2",
        status: "AWAITING_PLAN_CONFIRMATION",
        answer: "Confirm the bounded plan before draft mutation.",
        plan: { request: "Design an ontology for receivables.", steps: ["Inspect the approved project", "Prepare typed edits", "Run deterministic analysis"], openDecisions: ["Confirm receivables scope"], estimatedEditCount: 1 },
      }));
      return json(route, aiTurn({
        runId: "run-2",
        answer: "I prepared one private typed edit for deterministic analysis.",
        draftId: "draft-1",
        messages: [aiMessage("message-3", "ASSISTANT", "I prepared one private typed edit for deterministic analysis.")],
      }));
    }
    if (path.includes("/ai/runs/") && path.endsWith("/events")) {
      const runId = path.split("/").at(-2) ?? "run-1";
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: `id: ${runId}:1\nevent: run-started\ndata: {"sequence":1,"runId":"${runId}","type":"RUN_STARTED","message":"Run started.","referenceIds":[],"createdAt":"2026-07-17T12:00:00Z"}\n\nid: ${runId}:2\nevent: capability-completed\ndata: {"sequence":2,"runId":"${runId}","type":"CAPABILITY_COMPLETED","message":"Entity inspection completed.","referenceIds":["entity:Customer"],"createdAt":"2026-07-17T12:00:01Z"}\n\n` });
    }
    if (path.endsWith("/ai/drafts/draft-1") && request.method() === "GET") return json(route, { apiVersion: "v1", draft: aiDraft(aiDraftAnalyzed ? "READY_FOR_REVIEW" : "EDITING") });
    if (path.endsWith("/ai/drafts/draft-1/analysis") && request.method() === "POST") {
      aiDraftAnalyzed = true;
      return json(route, { apiVersion: "v1", analysis: aiAnalysis() });
    }
    if (path.endsWith("/ai/drafts/draft-1/submit") && request.method() === "POST") return json(route, {
      apiVersion: "v1", submissionId: "submission-1", proposalId: "proposal-ai-1", reviewState: "READYFORREVIEW", projectId: "simple", draftId: "draft-1", draftRevision: 1, submittingUserId: "alice", conversationId: "conversation-1", runId: "run-2", rationale: "Reviewed AI draft", itemAttributions: [{ itemId: "item-1", aiGenerated: true, acceptingUserId: "alice", conversationId: "conversation-1", runId: "run-2" }], diff: [{ kind: "Added", subject: "Receivable Account", predicate: "type", objectValue: "Class", description: "Added class Receivable Account." }], analysisReferenceIds: ["analysis-ref"], reviewRoute: "/projects/simple/changes?proposalId=proposal-ai-1",
    });
    if (path.endsWith("/staged") && request.method() === "GET") return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
    if (path.endsWith("/staged") && request.method() === "POST") {
      stagedEditType = (request.postDataJSON() as { editType?: string }).editType;
      return json(route, {
        apiVersion: "v1", projectId: "simple", status: "READY", entries: [{ id: "stage-1", order: 1, sourceId: "simple", summary: "create object property", editType: stagedEditType, status: "STAGED", authorId: "alice", latestEditorId: "alice", comment: null, aiGenerated: false, normalizedValues: {}, generatedIris: [], validationMessages: [] }], proposal: null,
      });
    }
    if (path.endsWith("/proposal/preview")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [{ id: "stage-1", order: 1, sourceId: "simple", summary: "add superclass", editType: "add-superclass", status: "STAGED", authorId: "alice", latestEditorId: "alice", comment: null, aiGenerated: true, normalizedValues: {}, generatedIris: [], validationMessages: [] }], proposal: { id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: ["stage-1"], baselineProjectFingerprint: "base", validationMessages: [], diff: [{ kind: "Added", subject: "Customer", predicate: "subClassOf", objectValue: "Party", description: "Customer is a Party" }], message: "Proposal ready for review." } });
    if (path.endsWith("/proposal/approve")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: { id: "proposal-1", status: "APPROVED", stagedChangeIds: ["stage-1"], baselineProjectFingerprint: "base", validationMessages: [], diff: [], message: "Approved." } });
    if (path.endsWith("/proposal/apply")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: { id: "proposal-1", status: "APPLIED", stagedChangeIds: [], baselineProjectFingerprint: "base", validationMessages: [], diff: [], message: "Applied and reloaded." } });
    if (path.endsWith("/external/fibo/modules")) return json(route, { sourceId: "fibo", release: "test", page: { items: [{ ontologyIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", label: "Agreements", domain: "FND", sourcePath: "source/FND/Agreements/Agreements.rdf", maturity: "Release", curated: true, elementCount: 1 }], offset: 0, limit: 15, total: 1, nextOffset: null } });
    if (path.endsWith("/external/fibo/module-elements")) return json(route, { moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", page: { items: [{ iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement", label: "agreement", kind: "Class", moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", domain: "FND", maturity: "Release", catalogStatus: "Available", sourcePath: "source/FND/Agreements/Agreements.rdf", alternateLabels: [], definitions: ["a mutual understanding"], parents: [], domains: [], ranges: [] }], offset: 0, limit: 15, total: 1, nextOffset: null } });
    if (path.endsWith("/external/fibo/details")) return json(route, { element: { iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement", label: "agreement", kind: "Class", moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", domain: "FND", maturity: "Release", catalogStatus: "Available", sourcePath: "source/FND/Agreements/Agreements.rdf", alternateLabels: [], definitions: ["a mutual understanding"], parents: [], domains: [], ranges: [] }, dependencies: [] });
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/");
  await page.getByRole("link", { name: /Simple ontology/ }).click();
  await expect(page.getByRole("heading", { name: "simple-ontology" })).toBeVisible();
  await page.getByRole("button", { name: /Customer/ }).click();
  await expect(page.getByRole("heading", { name: "Customer" })).toBeVisible();
  await expect(page.getByText("A customer.")).toBeVisible();
  await expect(page).toHaveScreenshot("workbench-light.png", { fullPage: true, mask: [page.locator(".collaboration-presence")], maskColor: "#ffffff" });

  await page.getByRole("button", { name: "Expand navigation" }).click();
  await expect(page.getByRole("tab", { name: "Explore", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await expect(page.getByText("Local reviewer", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Open settings" }).click();
  await expect(page.getByRole("tab", { name: "Settings", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();
  await page.getByRole("textbox", { name: "Display name" }).fill("Shrey Sharma");
  await page.getByRole("button", { name: "Save name" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Display name updated." })).toBeVisible();
  await expect(page.getByText("Provider credential", { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "simple-ontology" })).toBeVisible();
  await page.getByRole("button", { name: "Expand navigation" }).click();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await expect(page.getByText("Shrey Sharma", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.getByRole("tab", { name: "Explore", exact: true }).click();

  await page.getByLabel("Change type").selectOption("create-object-property");
  await page.getByRole("textbox", { name: "Property label", exact: true }).fill("owns account");
  await page.getByRole("button", { name: "Stage change" }).click();
  await expect(page.getByText("create object property", { exact: true })).toBeVisible();
  expect(stagedEditType).toBe("create-object-property");
  await page.getByRole("button", { name: "Preview proposal" }).click();
  await expect(page.getByText(/Proposal ready for review/i)).toBeVisible();

  await page.getByRole("button", { name: "Assistant" }).click();
  await expect(page.getByRole("complementary", { name: "Entio AI assistant" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Explore", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByText("Ready when you are")).toBeVisible();
  await page.getByLabel("Ask about this ontology context").fill("Explain Customer.");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Customer is an asserted class in the selected project.")).toBeVisible();
  await expect(page.getByText("Entity inspection completed.")).toBeVisible();
  await page.getByLabel("Ask about this ontology context").fill("Design an ontology for receivables.");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByRole("heading", { name: "Confirm the plan" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm plan" }).click();
  await expect(page.getByRole("heading", { name: "Private draft" })).toBeVisible();
  await expect(page.getByText("Create Receivable Account")).toBeVisible();
  await page.getByRole("button", { name: "Run deterministic analysis" }).click();
  await expect(page.getByText("Ready for human review.")).toBeVisible();
  await page.getByRole("button", { name: "Submit for human review" }).click();
  await expect(page.getByRole("link", { name: "Open proposal review" })).toHaveAttribute("href", "/projects/simple/changes?proposalId=proposal-ai-1");

  await page.getByRole("tab", { name: "FIBO" }).click();
  await expect(page.getByRole("heading", { name: "External ontology browser" })).toBeVisible();
  await page.getByRole("button", { name: "Agreements" }).click();
  await page.locator(".external-browser-grid .external-scroll-list").nth(1).getByRole("button").click();
  await expect(page.getByText("a mutual understanding")).toBeVisible();
  await expect(page.getByText(/https:\/\/spec\.edmcouncil\.org\/fibo\/ontology\/FND\/Agreements/)).toBeVisible();
});

async function json(route: import("@playwright/test").Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}

function aiConversation(messages: unknown[], currentDraftId: string | null = null) {
  return { id: "conversation-1", projectId: "simple", messages, currentDraftId, modelId: "gpt-5.2", status: "ACTIVE", createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" };
}

function aiMessage(id: string, role: "USER" | "ASSISTANT", content: string, operation: string | null = null, evidenceReferenceIds: string[] = []) {
  return { id, role, content, operation, evidenceReferenceIds, createdAt: "2026-07-17T12:00:00Z" };
}

function aiTurn({ runId, status = "READY_FOR_REVIEW", answer, messages = [], plan = null, draftId = null }: { runId: string; status?: string; answer: string; messages?: unknown[]; plan?: unknown; draftId?: string | null }) {
  return { apiVersion: "v1", conversation: aiConversation(messages, draftId), run: { id: runId, conversationId: "conversation-1", projectId: "simple", status, capabilityCallCount: 1, draftEditCount: draftId ? 1 : 0, correctionCycleCount: 0, cancellationRequested: false, createdAt: "2026-07-17T12:00:00Z", updatedAt: `${Date.now()}` }, intent: plan ? "BROAD_PLAN" : draftId ? "SMALL_EDIT" : "EXPLANATION", answer, plan, clarificationQuestion: null, draftId, limits: [] };
}

function aiDraft(status: string) {
  return { id: "draft-1", conversationId: "conversation-1", projectId: "simple", baselineFingerprint: "baseline", allowedSourceIds: ["simple"], status, draftFingerprint: "draft-fingerprint", analysisReferenceIds: [], items: [{ id: "item-1", order: 1, capabilityName: "entio_draft_add_ontology_edit", targetSourceId: "simple", summary: "Create Receivable Account", rationale: "Represent the reviewed accounting concept.", dependencyItemIds: [], aiGenerated: true, acceptingUserId: "alice", runId: "run-2" }], revisions: [{ revision: 1, action: "ADD", explanation: "Prepared typed class edit.", itemIds: ["item-1"], undoneRevision: null, createdAt: "2026-07-17T12:00:00Z" }], createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" };
}

function aiAnalysis() {
  return { id: "analysis-1", draftId: "draft-1", revision: 1, status: "COMPLETED", baselineFingerprint: "baseline", draftFingerprint: "draft-fingerprint", previewGraphFingerprint: "preview-fingerprint", readyForReview: true, validationOk: true, findings: [], diff: [{ kind: "ADDED", subject: "Receivable Account", predicate: "type", objectValue: "Class", description: "Added class Receivable Account." }], references: [{ stage: "VALIDATION", id: "analysis-ref" }], createdAt: "2026-07-17T12:00:00Z" };
}
