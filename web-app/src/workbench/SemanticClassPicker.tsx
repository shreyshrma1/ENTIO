import SemanticEntityPicker, { type SemanticEntityChoice } from "./SemanticEntityPicker";
import type { WebDomainRecommendationRequest } from "../web/projectApi";

export type SemanticClassChoice = SemanticEntityChoice;

interface SemanticClassPickerProps {
  projectId: string;
  id: string;
  label: string;
  selected: SemanticClassChoice[];
  onChange: (selected: SemanticClassChoice[]) => void;
  multiple?: boolean;
  excludeIri?: string;
  selectedValueInInput?: boolean;
  required?: boolean;
  selectionPresentation?: "chips" | "list" | "hidden";
  appliedIris?: readonly string[];
  removableApplied?: boolean;
  domainRecommendation?: Omit<WebDomainRecommendationRequest, "draftLabel">;
  domainLocalTarget?: { iri: string; sourceId: string };
}

export default function SemanticClassPicker({
  projectId,
  id,
  label,
  selected,
  onChange,
  multiple = true,
  excludeIri,
  selectedValueInInput = false,
  required = false,
  selectionPresentation = "chips",
  appliedIris,
  removableApplied = true,
  domainRecommendation,
  domainLocalTarget,
}: SemanticClassPickerProps) {
  return <SemanticEntityPicker
    projectId={projectId}
    id={id}
    label={label}
    selected={selected}
    onChange={onChange}
    accepts={(kind) => kind.toLocaleLowerCase() === "class"}
    placeholder="Search existing or staged classes"
    help="Choose only classes that already exist or are currently staged."
    multiple={multiple}
    excludeIri={excludeIri}
    selectedValueInInput={selectedValueInInput}
    required={required}
    selectionPresentation={selectionPresentation}
    appliedIris={appliedIris}
    removableApplied={removableApplied}
    domainRecommendation={domainRecommendation}
    domainLocalTarget={domainLocalTarget}
  />;
}
