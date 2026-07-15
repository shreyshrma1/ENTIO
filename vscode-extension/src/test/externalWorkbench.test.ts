import { strict as assert } from "node:assert";
import { test } from "node:test";
import {
  createExternalBrowseModel,
  createExternalDependenciesModel,
  createExternalDescriptorModel,
  createExternalManifestModel,
  createExternalProposalModel,
  createExternalSearchModel,
  externalCandidateIri,
  externalCandidateLabel,
  externalDependencyKey,
  type ExternalDependencyModel,
} from "../externalWorkbench";

test("normalizes external manifest and curated browse responses", () => {
  const manifest = createExternalManifestModel({
    ok: true,
    source: { id: "fibo" },
    manifest: { release: "master_2026Q2", commitSha: "abc", catalogSchema: "fibo-catalog-v1" },
    catalog: { elementCount: 10, moduleCount: 2 },
    package: { availability: "available" },
  });
  const browse = createExternalBrowseModel({
    ok: true,
    mode: "curated",
    page: { items: [{ ontologyIri: "https://example.com/fibo" }], totalCount: 1, page: 1, pageSize: 25, hasNext: false },
  });

  assert.deepEqual(manifest, {
    sourceId: "fibo",
    release: "master_2026Q2",
    commitSha: "abc",
    catalogSchema: "fibo-catalog-v1",
    elementCount: 10,
    moduleCount: 2,
    availability: "available",
  });
  assert.equal(browse?.mode, "curated");
  assert.equal(browse?.items.length, 1);
  assert.equal(browse?.totalCount, 1);
});

test("normalizes empty, ambiguous, and paginated external search results", () => {
  const result = createExternalSearchModel({
    ok: true,
    query: "account",
    totalResultCount: 2,
    page: 1,
    pageSize: 25,
    hasNext: false,
    candidates: [
      {
        score: 100,
        confidence: "high",
        tieGroupId: "tie-1",
        element: {
          kind: "Class",
          descriptor: {
            semantic: {
              iri: "https://example.com/fibo/Account",
              preferredLabel: { value: "Account" },
            },
          },
        },
      },
    ],
  });
  const empty = createExternalSearchModel({
    ok: true,
    query: "missing",
    totalResultCount: 0,
    page: 1,
    pageSize: 25,
    hasNext: false,
    candidates: [],
  });

  assert.equal(result?.candidates.length, 1);
  assert.equal(externalCandidateLabel(result!.candidates[0]), "Account");
  assert.equal(externalCandidateIri(result!.candidates[0]), "https://example.com/fibo/Account");
  assert.equal(result?.candidates[0].tieGroupId, "tie-1");
  assert.equal(empty?.totalResultCount, 0);
});

test("normalizes descriptor, dependency, and proposal states", () => {
  const descriptor = createExternalDescriptorModel({
    ok: true,
    element: {
      kind: "Class",
      descriptor: {
        semantic: { iri: "https://example.com/fibo/Account", preferredLabel: { value: "Account" } },
        moduleIri: "https://example.com/fibo/module",
        maturity: "provisional",
      },
    },
  });
  const dependencies = createExternalDependenciesModel({
    ok: true,
    requiresExplicitApproval: true,
    dependencySet: {
      dependencies: [{
        category: "SourceOntology",
        selection: "Missing",
        requirement: "Required",
        reason: "Source module is required",
        externalIri: "https://example.com/fibo/module",
        sourceModule: "module",
      }],
      status: "blocked",
    },
  });
  const proposal = createExternalProposalModel({
    ok: true,
    proposal: {
      id: "external-proposal",
      targetSourceId: "simple",
      changeCount: 1,
      preview: { tripleCount: 2 },
    },
    dependencySet: { status: "approved" },
  });

  assert.equal(descriptor?.kind, "Class");
  assert.equal(dependencies?.requiresExplicitApproval, true);
  assert.equal(dependencies?.dependencies[0].selection, "Missing");
  assert.equal(proposal?.dependencyStatus, "approved");
  const dependency = dependencies!.dependencies[0] as ExternalDependencyModel;
  assert.equal(externalDependencyKey(dependency), "SourceOntology|https://example.com/fibo/module|module");
});

test("rejects malformed or failed external responses", () => {
  assert.equal(createExternalManifestModel({ ok: false }), undefined);
  assert.equal(createExternalBrowseModel({ ok: true, page: { items: [] } }), undefined);
  assert.equal(createExternalDescriptorModel({ ok: true, element: {} }), undefined);
  assert.equal(createExternalSearchModel({ ok: true, candidates: [] }), undefined);
  assert.equal(createExternalDependenciesModel({ ok: true, requiresExplicitApproval: true }), undefined);
  assert.equal(createExternalProposalModel({ ok: false }), undefined);
});
