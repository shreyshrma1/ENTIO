import { describe, expect, it } from "vitest";
import { buildStageChangeRequest, STAGING_EDIT_DEFINITIONS } from "./stagingEditTypes";

describe("web staging edit definitions", () => {
  it("exposes every typed edit supported by the web boundary", () => {
    expect(STAGING_EDIT_DEFINITIONS.map((definition) => definition.type)).toEqual([
      "create-class",
      "set-entity-label",
      "add-superclass",
      "remove-superclass",
      "create-object-property",
      "create-datatype-property",
      "set-property-domain",
      "set-property-range",
      "create-individual",
      "assign-type",
      "add-object-property-assertion",
      "add-datatype-property-assertion",
    ]);
  });

  it("builds an object relationship request without changing semantic meaning", () => {
    expect(buildStageChangeRequest("simple", "add-object-property-assertion", {
      subjectLabel: " Shrey ",
      propertyLabel: "owns account",
      objectLabel: "Checking Account 1",
    }, "request-1")).toEqual({
      sourceId: "simple",
      editType: "add-object-property-assertion",
      subjectLabel: "Shrey",
      propertyLabel: "owns account",
      objectLabel: "Checking Account 1",
      idempotencyKey: "request-1",
    });
  });

  it("rejects a request when a required field is blank", () => {
    expect(() => buildStageChangeRequest("simple", "set-property-domain", {
      propertyLabel: "owns account",
    }, "request-2")).toThrow("Domain class label is required.");
  });
});
