import type { WebEntityReference } from "../../web/projectApi";
import type { OntologyMapViewState } from "./OntologyMapShell";

export type ExploreTab =
  | { kind: "entity"; entity: WebEntityReference }
  | { kind: "map"; state: OntologyMapViewState };

export function openMapTab(tabs: ExploreTab[], state: OntologyMapViewState): ExploreTab[] {
  return tabs.some((tab) => tab.kind === "map") ? tabs : [{ kind: "map", state }, ...tabs];
}

export function closeMapTab(tabs: ExploreTab[]): ExploreTab[] {
  return tabs.filter((tab) => tab.kind !== "map");
}
