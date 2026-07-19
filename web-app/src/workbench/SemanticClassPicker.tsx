import SemanticEntityPicker, { type SemanticEntityChoice } from "./SemanticEntityPicker";

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
  />;
}
