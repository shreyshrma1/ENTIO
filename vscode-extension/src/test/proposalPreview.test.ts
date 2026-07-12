import { strict as assert } from "node:assert";
import { test } from "node:test";
import {
  createProposalActionResult,
  createProposalPreviewModel,
  createEditFormState,
  editFormStateRequest,
  selectEditKind,
  updateEditFormField,
  proposalActionInvocationArgs,
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
  assert.deepEqual(proposalActionInvocationArgs("apply", "/workspace", request!), [
    "proposal-apply",
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

test("normalizes every approved edit kind through one request boundary", () => {
  const requests = [
    { editKind: "create-class", classIri: "https://example.com/Invoice" },
    { editKind: "create-object-property", propertyIri: "https://example.com/owns" },
    { editKind: "create-datatype-property", propertyIri: "https://example.com/name", datatype: "xsd:string" },
    { editKind: "set-property-domain", propertyIri: "https://example.com/owns", domainIri: "https://example.com/Customer" },
    { editKind: "set-property-range", propertyIri: "https://example.com/owns", rangeIri: "https://example.com/Account" },
    { editKind: "create-individual", individualIri: "https://example.com/alice" },
    { editKind: "assign-individual-type", individualIri: "https://example.com/alice", typeIri: "https://example.com/Customer" },
    { editKind: "add-object-property-assertion", subjectIri: "https://example.com/alice", propertyIri: "https://example.com/owns", objectIri: "https://example.com/account" },
    { editKind: "add-datatype-property-assertion", subjectIri: "https://example.com/alice", propertyIri: "https://example.com/name", value: "Alice" },
    { editKind: "add-superclass", classIri: "https://example.com/Customer", superclassIri: "https://example.com/Entity" },
    { editKind: "remove-superclass", classIri: "https://example.com/Customer", superclassIri: "https://example.com/Entity" },
    { editKind: "set-entity-label", entityIri: "https://example.com/Customer", label: "Client" },
  ];

  requests.forEach((request) => {
    const normalized = readProposalPreviewRequest({ targetSourceId: "simple", ...request });
    assert.ok(normalized, request.editKind);
    assert.equal(normalized?.editKind, request.editKind);
  });
});

test("form state changes preserve one shared request shape", () => {
  let state = createEditFormState("simple");
  state = selectEditKind(state, "create-object-property");
  state = updateEditFormField(state, "propertyIri", "https://example.com/owns");
  state = updateEditFormField(state, "domainIri", "https://example.com/Customer");
  state = updateEditFormField(state, "rangeIri", "https://example.com/Account");

  assert.deepEqual(editFormStateRequest(state), {
    targetSourceId: "simple",
    editKind: "create-object-property",
    propertyIri: "https://example.com/owns",
    domainIri: "https://example.com/Customer",
    rangeIri: "https://example.com/Account",
  });
});

test("serializes shared request fields into stable CLI options", () => {
  const request = readProposalPreviewRequest({
    targetSourceId: "simple",
    editKind: "add-datatype-property-assertion",
    subjectIri: "https://example.com/alice",
    propertyIri: "https://example.com/name",
    value: "Alice",
    language: "en",
  });

  assert.deepEqual(proposalPreviewCliArgs(request!), [
    "proposal-preview",
    "simple",
    "--edit",
    "add-datatype-property-assertion",
    "--subject-iri",
    "https://example.com/alice",
    "--property-iri",
    "https://example.com/name",
    "--value",
    "Alice",
    "--language",
    "en",
  ]);
});

test("normalizes each property form with its focused fields", () => {
  const forms = [
    {
      editKind: "create-object-property",
      propertyIri: "https://example.com/owns",
      label: "owns",
      domainIri: "https://example.com/Customer",
      rangeIri: "https://example.com/Account",
    },
    {
      editKind: "create-datatype-property",
      propertyIri: "https://example.com/name",
      datatype: "http://www.w3.org/2001/XMLSchema#string",
    },
    {
      editKind: "set-property-domain",
      propertyIri: "https://example.com/owns",
      domainIri: "https://example.com/Customer",
      replaceExisting: true,
    },
    {
      editKind: "set-property-range",
      propertyIri: "https://example.com/owns",
      datatype: "http://www.w3.org/2001/XMLSchema#string",
    },
  ];

  forms.forEach((form) => {
    const request = readProposalPreviewRequest({ targetSourceId: "simple", ...form });
    assert.ok(request, form.editKind);
    assert.equal(request?.editKind, form.editKind);
  });
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

test("normalizes applied, rejected, stale, and rollback action results", () => {
  const applied = createProposalActionResult("apply", {
    ok: true,
    proposalId: "proposal-1",
    status: "applied",
    changedFiles: ["/workspace/ontology/simple.ttl"],
    rollback: { status: "not-required" },
  });
  assert.ok(applied);
  assert.equal(applied.status, "applied");
  assert.deepEqual(applied.changedFiles, ["/workspace/ontology/simple.ttl"]);

  const rejected = createProposalActionResult("reject", {
    ok: true,
    proposal: { id: "proposal-2", status: "rejected" },
  });
  assert.ok(rejected);
  assert.equal(rejected.status, "rejected");

  const failed = createProposalActionResult("apply", {
    ok: false,
    proposalId: "proposal-3",
    status: "stale",
    error: { message: "Proposal is stale." },
    rollback: { status: "restored" },
  });
  assert.ok(failed);
  assert.equal(failed.ok, false);
  assert.equal(failed.status, "stale");
  assert.equal(failed.rollbackStatus, "restored");

  const validationFailure = createProposalActionResult("apply", {
    ok: false,
    proposal: {
      id: "proposal-4",
      status: "previewed",
      validation: { ok: false },
    },
  });
  assert.ok(validationFailure);
  assert.equal(validationFailure.status, "validation-failed");
});
