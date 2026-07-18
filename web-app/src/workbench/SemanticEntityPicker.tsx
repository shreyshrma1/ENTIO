import { useEffect, useMemo, useRef, useState } from "react";
import { useProjectSearch, useStagedChanges } from "../web/queries";

export interface SemanticEntityChoice {
  iri: string;
  label: string;
  kind: string;
  sourceId: string;
  staged: boolean;
}

interface SemanticEntityPickerProps {
  projectId: string;
  id: string;
  label: string;
  selected: SemanticEntityChoice[];
  onChange: (selected: SemanticEntityChoice[]) => void;
  accepts: (kind: string) => boolean;
  placeholder: string;
  help: string;
  multiple?: boolean;
  excludeIri?: string;
  includeStaged?: boolean;
  selectedValueInInput?: boolean;
  required?: boolean;
}

export default function SemanticEntityPicker({
  projectId,
  id,
  label,
  selected,
  onChange,
  accepts,
  placeholder,
  help,
  multiple = false,
  excludeIri,
  includeStaged = true,
  selectedValueInInput = false,
  required = false,
}: SemanticEntityPickerProps) {
  const root = useRef<HTMLDivElement>(null);
  const editingSelectedValue = useRef(false);
  const [input, setInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const [open, setOpen] = useState(false);
  const search = useProjectSearch(projectId, searchText);
  const staged = useStagedChanges(projectId);

  useEffect(() => {
    if (!selectedValueInInput) return;
    const choice = selected[0];
    if (choice) {
      setInput(choice.label);
      editingSelectedValue.current = false;
    } else if (!editingSelectedValue.current) {
      setInput("");
    } else {
      editingSelectedValue.current = false;
    }
  }, [selected, selectedValueInInput]);

  useEffect(() => {
    const timeout = window.setTimeout(() => setSearchText(input.trim()), 180);
    return () => window.clearTimeout(timeout);
  }, [input]);

  const options = useMemo(() => {
    const byIri = new Map<string, SemanticEntityChoice>();
    const normalizedInput = searchText.toLocaleLowerCase();
    (includeStaged ? stagedEntityChoices(staged.data?.entries ?? []) : [])
      .filter((choice) => accepts(choice.kind))
      .filter((choice) => !normalizedInput || choice.label.toLocaleLowerCase().includes(normalizedInput))
      .forEach((choice) => byIri.set(choice.iri, choice));
    (search.data?.page.items ?? [])
      .filter((item) => accepts(item.kind))
      .forEach((item) => {
        if (!byIri.has(item.iri)) {
          byIri.set(item.iri, { iri: item.iri, label: item.label, kind: item.kind, sourceId: item.sourceId, staged: false });
        }
      });
    const selectedIris = new Set(selected.map((choice) => choice.iri));
    return [...byIri.values()]
      .filter((choice) => choice.iri !== excludeIri && !selectedIris.has(choice.iri))
      .sort((left, right) => Number(right.staged) - Number(left.staged) || left.label.localeCompare(right.label));
  }, [accepts, excludeIri, includeStaged, search.data?.page.items, searchText, selected, staged.data?.entries]);

  function select(choice: SemanticEntityChoice) {
    onChange(multiple ? [...selected, choice] : [choice]);
    setInput(selectedValueInInput ? choice.label : "");
    setSearchText(selectedValueInInput ? choice.label : "");
    setOpen(false);
  }

  return <div
    className="semantic-entity-picker"
    ref={root}
    onBlur={(event) => {
      if (!root.current?.contains(event.relatedTarget as Node | null)) setOpen(false);
    }}
  >
    <label htmlFor={id}>{label}</label>
    {selected.length && !selectedValueInInput ? <ul className="entity-selection-list" aria-label={`Selected ${label.toLocaleLowerCase()}`}>
      {selected.map((choice) => <li key={choice.iri} className={choice.staged ? "entity-selection-staged" : undefined}>
        <span>{choice.label}</span>
        {choice.staged ? <small>Staged</small> : null}
        <button type="button" aria-label={`Remove ${choice.label}`} onClick={() => onChange(selected.filter((item) => item.iri !== choice.iri))}>×</button>
      </li>)}
    </ul> : null}
    <div className="semantic-entity-combobox">
      <input
        id={id}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open && input.trim().length > 0}
        aria-controls={`${id}-options`}
        value={input}
        onChange={(event) => {
          if (selectedValueInInput && selected.length) {
            editingSelectedValue.current = true;
            onChange([]);
          }
          setInput(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && options[0]) {
            event.preventDefault();
            select(options[0]);
          }
          if (event.key === "Escape") setOpen(false);
        }}
        placeholder={placeholder}
        required={required}
      />
      {open && input.trim() ? <div className="semantic-entity-options" id={`${id}-options`} role="listbox">
        {search.isPending ? <p role="status">Searching…</p> : null}
        {search.isError ? <p role="alert">Semantic search is unavailable.</p> : null}
        {!search.isPending && options.length === 0 ? <p>No matching applied or staged entities.</p> : null}
        {options.map((choice) => <button key={choice.iri} type="button" role="option" aria-selected="false" onClick={() => select(choice)}>
          <span>{choice.label}</span><small>{choice.staged ? `Staged ${displayKind(choice.kind)}` : displayKind(choice.kind)}</small>
        </button>)}
      </div> : null}
    </div>
    <small>{help}</small>
  </div>;
}

function stagedEntityChoices(entries: Array<{ editType: string; normalizedValues: Record<string, string> }>): SemanticEntityChoice[] {
  return entries.flatMap((entry) => {
    const config = stagedEntityConfig(entry.editType);
    if (!config) return [];
    const iri = entry.normalizedValues[config.iriKey];
    const label = entry.normalizedValues.label ?? entry.normalizedValues.individualLabel;
    if (!iri || !label) return [];
    return [{ iri, label, kind: config.kind, sourceId: "staged", staged: true }];
  });
}

function stagedEntityConfig(editType: string): { iriKey: string; kind: string } | null {
  if (editType === "create-class") return { iriKey: "classIri", kind: "Class" };
  if (editType === "create-object-property") return { iriKey: "propertyIri", kind: "ObjectProperty" };
  if (editType === "create-datatype-property") return { iriKey: "propertyIri", kind: "DatatypeProperty" };
  if (editType === "create-individual") return { iriKey: "individualIri", kind: "Individual" };
  return null;
}

function displayKind(kind: string) {
  return kind.replace(/([a-z])([A-Z])/g, "$1 $2").toLocaleLowerCase();
}
