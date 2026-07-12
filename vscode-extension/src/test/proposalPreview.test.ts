import { strict as assert } from "node:assert";
import { test } from "node:test";
import {
  createProposalPreviewModel,
  proposalPreviewCliArgs,
  proposalPreviewInvocationArgs,
  readProposalPreviewRequest,
} from "../proposalPreview";

test("creates a typed preview request and safe CLI arguments", () => {
  const request = readProposalPreviewRequest({
    targetSourceId: "simple",
    editKind: "create-class",
    classIri: "https://example.com/Invoice",
    label: "Invoice",
  });

  assert.deepEqual(request, {
    targetSourceId: "simple",
    editKind: "create-class",
    classIri: "https://example.com/Invoice",
    label: "Invoice",
  });
  assert.deepEqual(proposalPreviewCliArgs(request!), [
    "proposal-preview",
    "simple",
    "--edit",
    "create-class",
    "--class-iri",
    "https://example.com/Invoice",
    "--label",
    "Invoice",
  ]);
  assert.deepEqual(proposalPreviewInvocationArgs("/workspace", request!), [
    "proposal-preview",
    "/workspace",
    "simple",
    "--edit",
    "create-class",
    "--class-iri",
    "https://example.com/Invoice",
    "--label",
    "Invoice",
  ]);
});

test("rejects unsupported or incomplete preview requests", () => {
  assert.equal(readProposalPreviewRequest({ editKind: "create-class", classIri: "" }), undefined);
  assert.equal(readProposalPreviewRequest({ targetSourceId: "simple", editKind: "add-triple", classIri: "x" }), undefined);
});

test("normalizes a valid preview and enables only semantic approval readiness", () => {
  const preview = createProposalPreviewModel({
    ok: true,
    proposal: {
      id: "proposal-1",
      status: "previewed",
      targetSourceId: "simple",
      sourceFileImpact: { affectedPaths: ["ontology/simple.ttl"] },
      preview: { tripleCount: 8 },
      diff: { entries: [{ kind: "added", description: "Added class." }] },
      validation: { status: "valid", ok: true, issues: [] },
      semanticEquivalence: { status: "equivalent" },
    },
  });

  assert.ok(preview);
  assert.equal(preview.canApprove, true);
  assert.deepEqual(preview.affectedPaths, ["ontology/simple.ttl"]);
  assert.deepEqual(preview.diffEntries.map((entry) => entry.description), ["Added class."]);
});

test("disables approval when validation fails", () => {
  const preview = createProposalPreviewModel({
    ok: true,
    proposal: {
      id: "proposal-2",
      status: "previewed",
      targetSourceId: "simple",
      sourceFileImpact: { affectedPaths: [] },
      preview: { tripleCount: 7 },
      diff: { entries: [] },
      validation: { status: "invalid", ok: false, issues: [{ message: "Invalid class IRI." }] },
      semanticEquivalence: { status: "equivalent" },
    },
  });

  assert.ok(preview);
  assert.equal(preview.canApprove, false);
  assert.match(preview.approvalDisabledReason!, /validation failed/);
});
