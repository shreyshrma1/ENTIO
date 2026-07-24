import { expect, test, type Route } from "@playwright/test";

test("completes the accessible document review and proposal workflow", async ({ page }) => {
  let uploaded = false;
  let accepted = false;
  let staged = false;
  let proposalStatus: "READYFORREVIEW" | "APPROVED" | "APPLIED" | null = null;
  let multipartBody = "";

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 3 }],
      symbolCount: 2,
      graphTripleCount: 3,
    });
    if (path.endsWith("/sources")) return json(route, {
      items: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], writable: true }],
      offset: 0, limit: 50, total: 1, nextOffset: null,
    });
    if (path.endsWith("/hierarchy")) return json(route, {
      apiVersion: "v1", sourceId: "simple", parentIri: null,
      page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null },
    });
    if (path.endsWith("/outline")) return json(route, {
      apiVersion: "v1", sourceId: "simple",
      page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null },
    });
    if (path.endsWith("/staged") && request.method() === "GET") return json(route, staging(staged, proposalStatus));
    if (path.endsWith("/proposal/preview")) {
      proposalStatus = "READYFORREVIEW";
      return json(route, staging(staged, proposalStatus));
    }
    if (path.endsWith("/proposal/approve")) {
      proposalStatus = "APPROVED";
      return json(route, staging(staged, proposalStatus));
    }
    if (path.endsWith("/proposal/apply")) {
      proposalStatus = "APPLIED";
      staged = false;
      return json(route, staging(staged, proposalStatus));
    }
    if (path.endsWith("/document-ingestion/tasks") && request.method() === "POST") {
      multipartBody = request.postData() ?? "";
      uploaded = true;
      return json(route, task);
    }
    if (path.endsWith("/document-ingestion/tasks")) {
      return json(route, {
        items: uploaded ? [task] : [],
        offset: 0, limit: 50, total: uploaded ? 1 : 0, nextOffset: null,
      });
    }
    if (path.includes("/evidence/")) return json(route, evidence);
    if (path.includes("/recommendations/") && path.endsWith("/decision")) {
      const body = request.postDataJSON() as { action: string; clarification?: string };
      expect(body.action).toBe("accept");
      expect(body.clarification).toBe("The later amendment governs.");
      accepted = true;
      return json(route, reviewWorkspace(accepted));
    }
    if (path.endsWith("/review")) return json(route, reviewWorkspace(accepted));
    if (path.endsWith("/draft")) {
      staged = true;
      return json(route, {
        apiVersion: "v1",
        staging: staging(staged, proposalStatus),
        batchCount: 1,
        stagedEditCount: 1,
        confirmCount: 0,
      });
    }
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/projects/simple");
  await page.getByRole("tab", { name: "Documents" }).click();
  await expect(page.getByRole("tabpanel", { name: "Documents workspace" })).toBeVisible();

  const input = page.locator('input[type="file"]');
  await input.setInputFiles([
    file("text.pdf", "%PDF-1.7 embedded text", "application/pdf"),
    file("scan.pdf", "%PDF-1.7 scanned page", "application/pdf"),
    file("mixed.pdf", "%PDF-1.7 embedded and scanned pages", "application/pdf"),
    file("policy.docx", "PK\u0003\u0004mock-docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    file("policy.txt", "Supplier policy", "text/plain"),
    file("amendment.md", "# Supplier amendment", "text/markdown"),
  ]);
  await page.getByLabel("Authority").selectOption("Authoritative");
  await page.getByLabel("Business area").fill("Procurement");
  await page.getByRole("button", { name: "Upload and analyze" }).press("Enter");

  await expect(page.getByRole("heading", { name: "Ontology structure" })).toBeVisible();
  for (const filename of ["text.pdf", "scan.pdf", "mixed.pdf", "policy.docx", "policy.txt", "amendment.md"]) {
    expect(multipartBody).toContain(filename);
  }
  await expect(page.getByText("Conflict requires review")).toBeVisible();
  await expect(page.getByText("Prior workflow evidence: applied-document-change-1")).toBeVisible();

  const evidenceButton = page.getByRole("button", { name: "Open Explicit evidence" });
  await evidenceButton.focus();
  await evidenceButton.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Evidence" });
  await expect(dialog.getByRole("heading", { name: "Evidence" })).toBeFocused();
  await expect(dialog.getByLabel("Extracted evidence text")).toContainText("Supplier");
  await dialog.getByRole("button", { name: "Close evidence viewer" }).press("Enter");

  await page.getByLabel("Clarification").fill("The later amendment governs.");
  const accept = page.getByRole("button", { name: "Accept", exact: true });
  await accept.focus();
  await accept.press("Enter");
  await expect(page.getByText("1 accepted · 0 pending · 1 blocked")).toBeVisible();
  await page.getByRole("button", { name: "Add accepted items to proposal" }).press("Enter");
  await expect(page.getByRole("status").filter({ hasText: "1 typed edit added" })).toBeVisible();
  await expect(page.getByLabel("Shared staged changes")).toContainText("1 change staged");

  await page.getByRole("button", { name: "Review proposal" }).press("Enter");
  await expect(page.getByRole("region", { name: "Review queue" })).toBeVisible();
  await expect(page.getByText("Proposal ready for review.")).toBeVisible();
  await page.getByRole("button", { name: "View Details" }).press("Enter");
  const proposal = page.getByRole("dialog", { name: "View Details" });
  await expect(proposal.getByLabel("Document recommendation provenance")).toContainText("Accepted document recommendation");
  await expect(proposal.getByLabel("Document recommendation provenance")).toContainText("1 verified reference");
  await proposal.getByRole("button", { name: "Accept" }).press("Enter");
  await expect(page.getByRole("status").filter({ hasText: "Proposal accepted and applied" })).toBeVisible();
  expect(proposalStatus).toBe("APPLIED");
});

function file(name: string, contents: string, mimeType: string) {
  return { name, mimeType, buffer: Buffer.from(contents) };
}

const task = {
  taskId: "task-document-e2e",
  projectId: "simple",
  ownerUserId: "alice",
  status: "awaiting-review",
  createdAt: "2026-07-24T12:00:00Z",
  updatedAt: "2026-07-24T12:01:00Z",
  documents: [{ documentId: "document-amendment", safeFilename: "amendment.md", mediaType: "markdown", byteSize: 20, checksumSha256: "a".repeat(64), authorityStatus: "authoritative", status: "awaiting-review" }],
  progress: { stage: "awaiting-review", completedDocuments: 6, totalDocuments: 6, percent: 100, message: "Evidence-linked recommendations are ready for review." },
};

function reviewWorkspace(accepted: boolean) {
  return {
    apiVersion: "v1",
    taskId: task.taskId,
    projectId: "simple",
    exactWorkKey: "document-work-key",
    graphFingerprint: "graph-fingerprint",
    documents: [{ documentId: "document-amendment", safeFilename: "amendment.md", mediaType: "markdown", authorityStatus: "authoritative", pageCount: null, warningCount: 0 }],
    summaries: [{ documentId: "document-amendment", purpose: "Revises the supplier definition.", highlights: ["Supplier"] }],
    recommendations: {
      items: [{
        id: "recommendation-supplier",
        category: "OntologyStructure",
        type: "Class",
        action: "Extend",
        proposedLabel: "Supplier",
        confidence: 94,
        confidenceBand: "High",
        rationale: "The amendment explicitly revises Supplier.",
        reviewStatus: accepted ? "Accepted" : "Pending",
        evidence: [{ evidenceId: "evidence-supplier", evidenceType: "Explicit", documentId: "document-amendment", pageNumber: null, extractionMethod: "Markdown", ocrConfidence: null, excerpt: "Supplier", priorRecordId: null }],
        matches: [{ scope: "AppliedLocal", entityIri: "https://example.com/simple#Supplier", sourceId: "simple", preferredLabel: "Supplier", score: 100, reason: "Exact label." }],
        selectedMatchIri: "https://example.com/simple#Supplier",
        conflicts: [{ id: "conflict-amendment", alternatives: ["Earlier policy", "Later amendment"], affectedEntityIris: ["https://example.com/simple#Supplier"], resolutionOptions: ["retain", "revise"] }],
        mandatoryClarificationReasons: ["Choose which document governs."],
        clarification: accepted ? "The later amendment governs." : null,
        targetSourceId: "simple",
        reconsiderationCount: 0,
        priorWorkflowProvenance: ["applied-document-change-1"],
      }],
      offset: 0, limit: 100, total: 1, nextOffset: null,
    },
    draftImpact: { acceptedCount: accepted ? 1 : 0, pendingCount: accepted ? 0 : 1, blockedCount: 1, maximumAcceptedEdits: 100, readOnly: true },
  };
}

const evidence = {
  apiVersion: "v1",
  evidenceId: "evidence-supplier",
  documentId: "document-amendment",
  safeFilename: "amendment.md",
  pageNumber: null,
  sectionHeading: "Supplier amendment",
  extractionMethod: "Markdown",
  ocrConfidence: null,
  text: "The Supplier definition is revised by this amendment.",
  highlightStart: 4,
  highlightEnd: 12,
  pageImageAvailable: false,
  truncated: false,
};

function staging(staged: boolean, proposalStatus: "READYFORREVIEW" | "APPROVED" | "APPLIED" | null) {
  const entries = staged ? [{
    id: "stage-document-1",
    order: 1,
    sourceId: "simple",
    summary: "edit-label · Supplier",
    editType: "TypedEdit",
    status: "STAGED",
    authorId: "alice",
    latestEditorId: "alice",
    comment: null,
    normalizedValues: { operation: "changed", subjectLabel: "Supplier", predicateLabel: "label", objectLabel: "Supplier" },
    generatedIris: [],
    validationMessages: [],
    documentDraftProvenance: {
      taskId: task.taskId,
      recommendationId: "recommendation-supplier",
      documentIds: ["document-amendment"],
      evidenceIds: ["evidence-supplier"],
      confidence: 94,
      confidenceBand: "High",
      authorityStatuses: ["Authoritative"],
      modelId: "gpt-test",
      promptVersion: "phase-11-document-analysis-v1",
      targetSourceId: "simple",
    },
  }] : [];
  const proposal = proposalStatus ? {
    id: "proposal-document-1",
    status: proposalStatus,
    stagedChangeIds: entries.map((entry) => entry.id),
    baselineProjectFingerprint: "base",
    validationMessages: [],
    validationIssues: [],
    diff: proposalStatus === "APPLIED" ? [] : [{
      kind: "Added",
      subject: "https://example.com/simple#Supplier",
      predicate: "http://www.w3.org/2000/01/rdf-schema#label",
      objectValue: "\"Supplier\"",
      description: "Updated supplier label.",
    }],
    message: proposalStatus === "READYFORREVIEW" ? "Proposal ready for review." : "Applied and reloaded.",
  } : null;
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal };
}

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}
