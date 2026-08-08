import { describe, expect, it } from "vitest";
import { CORE_AUTHORING_RECOMMENDATION_MATRIX, recommendationConfigForEdit } from "./domainRecommendationIntegration";

describe("Phase 13 contextual authoring coverage", () => {
  it("keeps every approved core authoring context in one executable matrix", () => {
    expect(CORE_AUTHORING_RECOMMENDATION_MATRIX.map((row) => row.workflow)).toEqual([
      "create-class",
      "create-object-property",
      "create-datatype-property",
      "individual-type",
      "label-definition",
      "class-hierarchy",
      "property-hierarchy",
      "domain",
      "range",
      "datatype",
      "object-assertion",
      "datatype-value",
    ]);
    expect(CORE_AUTHORING_RECOMMENDATION_MATRIX.every((row) => row.intentFields.includes("targetSourceId"))).toBe(true);
    expect(CORE_AUTHORING_RECOMMENDATION_MATRIX.every((row) => row.slice === 8 && row.component && row.acceptance)).toBe(true);
    expect(new Set(CORE_AUTHORING_RECOMMENDATION_MATRIX.map((row) => row.operationKind))).toEqual(new Set([
      "CreateClass", "CreateObjectProperty", "CreateDatatypeProperty", "CreateIndividualTypeSelection",
      "EditLabelOrDefinition", "EditClassHierarchy", "EditPropertyHierarchy", "EditDomain",
      "EditRangeOrDatatype", "AddAssertionOrValue",
    ]));
    expect(new Set(CORE_AUTHORING_RECOMMENDATION_MATRIX.flatMap((row) => row.intentFields))).toEqual(new Set([
      "draftLabel", "alternateWording", "definition", "currentEntityIri", "requiredParentIri",
      "requiredDomainIri", "requiredRangeIri", "requiredDatatypeIri", "nearbyProjectIris", "targetSourceId",
    ]));
  });

  it("maps supported typed edits to strict server operation and entity kinds", () => {
    expect(recommendationConfigForEdit("create-class")).toMatchObject({ operationKind: "CreateClass", requestedKind: "Class" });
    expect(recommendationConfigForEdit("create-object-property")).toMatchObject({ operationKind: "CreateObjectProperty", requestedKind: "ObjectProperty" });
    expect(recommendationConfigForEdit("create-datatype-property")).toMatchObject({ operationKind: "CreateDatatypeProperty", requestedKind: "DatatypeProperty" });
    expect(recommendationConfigForEdit("add-object-property-assertion")).toMatchObject({ operationKind: "AddAssertionOrValue", requestedKind: "ObjectProperty" });
    expect(recommendationConfigForEdit("add-datatype-property-assertion")).toMatchObject({ operationKind: "AddAssertionOrValue", requestedKind: "DatatypeProperty" });
    expect(recommendationConfigForEdit("shacl-create-node-shape")).toBeNull();
  });
});
