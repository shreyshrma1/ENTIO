import { expect, test } from "@playwright/test";

test("completes the browser workbench journey through reviewable staging", async ({ page }) => {
  let stagedEditType: string | undefined;
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
    if (path.endsWith("/ai/conversations/conversation-1/messages") && request.method() === "POST") return json(route, {
      apiVersion: "v1",
      conversation: aiConversation([
        { id: "message-1", role: "USER", content: "Explain Customer.", operation: null, evidenceReferenceIds: [], createdAt: "2026-07-17T12:00:00Z" },
        { id: "message-2", role: "ASSISTANT", content: "Customer is an asserted class in the selected project.", operation: "EXPLAIN_ENTITY", evidenceReferenceIds: ["entity:Customer"], createdAt: "2026-07-17T12:00:01Z" },
      ]),
      run: { id: "run-1", conversationId: "conversation-1", projectId: "simple", status: "READY_FOR_REVIEW", capabilityCallCount: 1, draftEditCount: 0, correctionCycleCount: 0, cancellationRequested: false, createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" },
      intent: "EXPLANATION", answer: "Customer is an asserted class in the selected project.", plan: null, clarificationQuestion: null, draftId: null, limits: [],
    });
    if (path.endsWith("/ai/runs/run-1/events")) return route.fulfill({ status: 200, contentType: "text/event-stream", body: "id: run-1:1\nevent: run-started\ndata: {\"sequence\":1,\"runId\":\"run-1\",\"type\":\"RUN_STARTED\",\"message\":\"Run started.\",\"referenceIds\":[],\"createdAt\":\"2026-07-17T12:00:00Z\"}\n\nid: run-1:2\nevent: capability-completed\ndata: {\"sequence\":2,\"runId\":\"run-1\",\"type\":\"CAPABILITY_COMPLETED\",\"message\":\"Entity inspection completed.\",\"referenceIds\":[\"entity:Customer\"],\"createdAt\":\"2026-07-17T12:00:01Z\"}\n\n" });
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

function aiConversation(messages: unknown[]) {
  return { id: "conversation-1", projectId: "simple", messages, currentDraftId: null, modelId: "gpt-5.2", status: "ACTIVE", createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" };
}
