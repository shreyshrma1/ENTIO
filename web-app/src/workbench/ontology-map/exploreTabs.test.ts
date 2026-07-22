import { describe, expect, it } from "vitest";
import { closeMapTab, openMapTab, type ExploreTab } from "./exploreTabs";

describe("Explore map tab lifecycle", () => {
  it("opens one map alongside entity tabs, focuses by identity, and clears it on close", () => {
    const entity: ExploreTab = { kind: "entity", entity: { iri: "urn:customer", label: "Customer", kind: "Class", sourceId: "main" } };
    const opened = openMapTab([entity], { selectedNodeId: "node-1" });
    expect(openMapTab(opened, { selectedNodeId: null })).toBe(opened);
    expect(opened.map((tab) => tab.kind)).toEqual(["map", "entity"]);
    expect(closeMapTab(opened)).toEqual([entity]);
  });
});
