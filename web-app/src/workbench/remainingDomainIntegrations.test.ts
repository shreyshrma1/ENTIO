import { describe, expect, it } from "vitest";
import type { WebReasoningFact, WebStagedEntry } from "../web/projectApi";
import { domainFactContext } from "./SemanticJobPanel";
import { domainReviewCandidate } from "./StagingPanel";
import { REMAINING_WORKFLOW_RECOMMENDATION_MATRIX, domainKindForEntityKind } from "./domainRecommendationIntegration";

describe("Phase 13 remaining human-driven integrations", () => {
  it("covers every fixed Slice 9 operation without changing its owning semantic workflow", () => {
    expect(REMAINING_WORKFLOW_RECOMMENDATION_MATRIX.map((row) => row.operationKind)).toEqual([
      "DeleteOrReplaceEntity", "ProposalReuseReview", "ShaclTargetClass", "ShaclPropertyPath",
      "ShaclClassOrDatatypeConstraint", "OntologyMapRelatedSearch", "ReasoningWorkspaceRelatedSearch",
    ]);
    expect(REMAINING_WORKFLOW_RECOMMENDATION_MATRIX.every((row) => row.slice === 9 && row.component && row.invariant)).toBe(true);
  });

  it("prepares proposal checks only for new or substantially changed concepts", () => {
    const created = domainReviewCandidate(entry("create-object-property", { label: "owns account" }));
    const relabeled = domainReviewCandidate(entry("set-entity-label", { resourceIri: "https://example.com/Account", label: "Financial account" }));
    expect(created?.intent).toMatchObject({ operationKind: "ProposalReuseReview", requestedKind: "ObjectProperty" });
    expect(relabeled?.intent).toMatchObject({ operationKind: "ProposalReuseReview", currentEntityIri: "https://example.com/Account" });
    expect(domainReviewCandidate(entry("add-superclass", { superclassLabel: "Account" }))).toBeNull();
    expect(domainReviewCandidate(entry("domain-reuse", { canonicalIri: "https://example.com/FIBO" }))).toBeNull();
  });

  it("derives bounded related searches from asserted and inferred facts without a materialization action", () => {
    const inferred: WebReasoningFact = { kind: "SubclassRelationship", subject: "https://example.com/Checking", subjectLabel: "Checking", predicate: null, predicateLabel: null, objectValue: "https://example.com/Account", objectLabel: "Account", origin: "Inferred", sourceId: "simple" };
    const asserted: WebReasoningFact = { kind: "ObjectPropertyAssertion", subject: "https://example.com/A", subjectLabel: "A", predicate: "https://example.com/owns", predicateLabel: "owns", objectValue: "https://example.com/B", objectLabel: "B", origin: "Asserted", sourceId: "simple" };
    expect(domainFactContext(inferred, 0)).toMatchObject({ kind: "Class", label: "Account", origin: "Inferred" });
    expect(domainFactContext(asserted, 1)).toMatchObject({ kind: "ObjectProperty", label: "owns", origin: "Asserted" });
  });

  it("maps only reusable schema entity kinds", () => {
    expect(domainKindForEntityKind("Class")).toBe("Class");
    expect(domainKindForEntityKind("Object property")).toBe("ObjectProperty");
    expect(domainKindForEntityKind("DatatypeProperty")).toBe("DatatypeProperty");
    expect(domainKindForEntityKind("Individual")).toBeNull();
  });
});

function entry(editType: string, normalizedValues: Record<string, string>): WebStagedEntry {
  return { id: `stage-${editType}`, order: 1, sourceId: "simple", summary: `${editType} · test`, editType, status: "STAGED", authorId: "alice", latestEditorId: "alice", comment: null, normalizedValues, generatedIris: [], validationMessages: [] };
}
