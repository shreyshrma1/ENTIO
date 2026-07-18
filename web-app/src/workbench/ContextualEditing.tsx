import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  loadDeletionDependencies,
  type WebDeletionDependenciesResponse,
  type WebEntityReference,
  type WebStageChangeRequest,
} from "../web/projectApi";
import { useStagingActions } from "../web/queries";
import SemanticClassPicker, { type SemanticClassChoice } from "./SemanticClassPicker";
import {
  buildStageChangeRequest,
  stagingEditDefinition,
  type StagingFormValues,
  type WebStagingEditType,
} from "./stagingEditTypes";

export interface ContextMenuAction {
  label: string;
  tone?: "danger";
  onSelect: () => void;
}

export interface ContextMenuState {
  x: number;
  y: number;
  label: string;
  actions: ContextMenuAction[];
}

export type ContextualEditor =
  | { kind: "class"; parent?: WebEntityReference }
  | { kind: "typed"; editType: WebStagingEditType; initialValues?: StagingFormValues }
  | { kind: "delete"; entity: WebEntityReference };

export function ContextMenu({ menu, onClose }: { menu: ContextMenuState; onClose: () => void }) {
  useEffect(() => {
    const close = () => onClose();
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("pointerdown", close);
    window.addEventListener("keydown", escape);
    return () => {
      window.removeEventListener("pointerdown", close);
      window.removeEventListener("keydown", escape);
    };
  }, [onClose]);

  return (
    <div
      className="context-menu"
      role="menu"
      aria-label={menu.label}
      style={{ left: Math.min(menu.x, window.innerWidth - 230), top: Math.min(menu.y, window.innerHeight - 180) }}
      onPointerDown={(event) => event.stopPropagation()}
    >
      <span className="context-menu-label">{menu.label}</span>
      {menu.actions.map((action) => <button className={action.tone === "danger" ? "context-menu-danger" : undefined} key={action.label} type="button" role="menuitem" onClick={() => { action.onSelect(); onClose(); }}>{action.label}</button>)}
    </div>
  );
}

export function ContextualEditDialog({
  projectId,
  sourceId,
  editor,
  onClose,
}: {
  projectId: string;
  sourceId: string;
  editor: ContextualEditor;
  onClose: () => void;
}) {
  useEffect(() => {
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", escape);
    return () => window.removeEventListener("keydown", escape);
  }, [onClose]);

  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    {editor.kind === "class"
      ? <ClassEditDialog projectId={projectId} sourceId={sourceId} parent={editor.parent} onClose={onClose} />
      : editor.kind === "delete"
        ? <DeletionDialog projectId={projectId} sourceId={sourceId} entity={editor.entity} onClose={onClose} />
        : <TypedEditDialog projectId={projectId} sourceId={sourceId} editType={editor.editType} initialValues={editor.initialValues} onClose={onClose} />}
  </div>;
}

function ClassEditDialog({ projectId, sourceId, parent, onClose }: { projectId: string; sourceId: string; parent?: WebEntityReference; onClose: () => void }) {
  const actions = useStagingActions(projectId);
  const [label, setLabel] = useState("");
  const [superclasses, setSuperclasses] = useState<SemanticClassChoice[]>(() => parent ? [{ iri: parent.iri, label: parent.label, kind: "Class", sourceId: parent.sourceId ?? sourceId, staged: false }] : []);
  const [associations, setAssociations] = useState<PropertyAssociation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [batchKey] = useState(() => `web-class-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  const busy = actions.stage.isPending;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const classLabel = label.trim();
    if (!classLabel) return setError("Class label is required.");
    setError(null);
    try {
      const created = await actions.stage.mutateAsync({
        sourceId,
        editType: "create-class",
        label: classLabel,
        idempotencyKey: `${batchKey}-create`,
      });
      const classIri = created.entries.find((entry) => entry.normalizedValues.label === classLabel && entry.generatedIris.length)?.generatedIris[0];
      if (!classIri) throw new Error("Entio did not return the generated class identifier.");

      for (const [index, superclass] of superclasses.entries()) {
        await actions.stage.mutateAsync({
          sourceId,
          editType: "add-superclass",
          classIri,
          classLabel,
          superclassIri: superclass.iri,
          superclassLabel: superclass.label,
          idempotencyKey: `${batchKey}-superclass-${index}`,
        });
      }
      for (const [index, association] of associations.entries()) {
        const propertyLabel = association.propertyLabel.trim();
        if (!propertyLabel) continue;
        const request: WebStageChangeRequest = association.relation === "domain"
          ? { sourceId, editType: "set-property-domain", propertyLabel, domainClassIri: classIri, domainClassLabel: classLabel }
          : { sourceId, editType: "set-property-range", propertyLabel, rangeIri: classIri, rangeLabel: classLabel };
        await actions.stage.mutateAsync({ ...request, idempotencyKey: `${batchKey}-property-${index}` });
      }
      onClose();
    } catch (failure) {
      setError(`${failure instanceof Error ? failure.message : "The class could not be staged."} Any completed items remain visible in the review queue.`);
    }
  }

  return <section className="edit-dialog" role="dialog" aria-modal="true" aria-labelledby="class-edit-heading">
    <DialogHeader eyebrow="Ontology edit" title={parent ? `Add subclass of ${parent.label}` : "Add class"} headingId="class-edit-heading" onClose={onClose} />
    <form onSubmit={submit}>
      <label htmlFor="context-class-label">Class label<input id="context-class-label" autoFocus value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Checking Account" required /></label>
      <SemanticClassPicker projectId={projectId} id="context-superclasses" label="Superclass labels" selected={superclasses} onChange={setSuperclasses} />
      <fieldset className="property-associations"><legend>Property associations</legend>
        {associations.map((association) => <div className="property-association-row" key={association.id}>
          <input aria-label="Property label" value={association.propertyLabel} onChange={(event) => setAssociations((current) => current.map((item) => item.id === association.id ? { ...item, propertyLabel: event.target.value } : item))} placeholder="owns account" />
          <select aria-label="Class role for property" value={association.relation} onChange={(event) => setAssociations((current) => current.map((item) => item.id === association.id ? { ...item, relation: event.target.value as PropertyAssociation["relation"] } : item))}><option value="domain">Class is domain</option><option value="range">Class is range</option></select>
          <button className="icon-button" type="button" aria-label="Remove property association" onClick={() => setAssociations((current) => current.filter((item) => item.id !== association.id))}>×</button>
        </div>)}
        <button className="button small" type="button" onClick={() => setAssociations((current) => [...current, { id: `${Date.now()}-${current.length}`, propertyLabel: "", relation: "domain" }])}>Add property association</button>
      </fieldset>
      {error ? <p className="workflow-error" role="alert">{error}</p> : null}
      <div className="dialog-actions"><button className="button" type="button" onClick={onClose}>Cancel</button><button className="button primary" type="submit" disabled={busy}>{busy ? "Staging…" : "Stage class"}</button></div>
    </form>
  </section>;
}

function TypedEditDialog({ projectId, sourceId, editType, initialValues = {}, onClose }: { projectId: string; sourceId: string; editType: WebStagingEditType; initialValues?: StagingFormValues; onClose: () => void }) {
  const actions = useStagingActions(projectId);
  const definition = useMemo(() => stagingEditDefinition(editType), [editType]);
  const [values, setValues] = useState<StagingFormValues>(initialValues);
  const [individualClass, setIndividualClass] = useState<SemanticClassChoice[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [idempotencyKey] = useState(() => `web-context-${Date.now()}-${Math.random().toString(36).slice(2)}`);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      setError(null);
      const selectedClass = editType === "create-individual" ? individualClass[0] : undefined;
      if (editType === "create-individual" && !selectedClass) throw new Error("Class is required.");
      const request = buildStageChangeRequest(
        sourceId,
        editType,
        selectedClass ? { ...values, classLabel: selectedClass.label } : values,
        idempotencyKey,
      );
      await actions.stage.mutateAsync(selectedClass ? {
        ...request,
        classIri: selectedClass.iri,
        classLabel: selectedClass.label,
      } : request);
      onClose();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The change could not be staged.");
    }
  }

  return <section className="edit-dialog" role="dialog" aria-modal="true" aria-labelledby="typed-edit-heading">
    <DialogHeader eyebrow={editType.startsWith("shacl-") ? "Constraint edit" : "Ontology edit"} title={definition.label} headingId="typed-edit-heading" onClose={onClose} />
    <p className="edit-dialog-description">{definition.description}</p>
    <form onSubmit={submit}>
      <div className="edit-dialog-fields">
        {definition.fields.filter((field) => editType !== "create-individual" || field.key !== "classLabel").map((field, index) => <label key={field.key} htmlFor={`context-${field.key}`}>{field.label}<input id={`context-${field.key}`} autoFocus={index === 0} value={values[field.key] ?? ""} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} placeholder={field.placeholder} required={field.required} /></label>)}
        {editType === "create-individual" ? <SemanticClassPicker
          projectId={projectId}
          id="context-individual-class"
          label="Class"
          selected={individualClass}
          onChange={setIndividualClass}
          multiple={false}
          selectedValueInInput
          required
        /> : null}
      </div>
      {error ? <p className="workflow-error" role="alert">{error}</p> : null}
      <div className="dialog-actions"><button className="button" type="button" onClick={onClose}>Cancel</button><button className="button primary" type="submit" disabled={actions.stage.isPending}>{actions.stage.isPending ? "Adding…" : "Add to review queue"}</button></div>
    </form>
  </section>;
}

function DeletionDialog({ projectId, sourceId, entity, onClose }: { projectId: string; sourceId: string; entity: WebEntityReference; onClose: () => void }) {
  const actions = useStagingActions(projectId);
  const [plan, setPlan] = useState<WebDeletionDependenciesResponse | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    loadDeletionDependencies(projectId, { sourceId, targetIri: entity.iri, targetLabel: entity.label })
      .then((response) => { if (active) setPlan(response); })
      .catch((failure) => { if (active) setError(failure instanceof Error ? failure.message : "Deletion dependencies could not be inspected."); });
    return () => { active = false; };
  }, [entity.iri, entity.label, projectId, sourceId]);

  const allDependenciesSelected = plan?.dependentStatements.every((dependency) => selectedKeys.has(dependency.key)) ?? false;

  async function stageDeletion() {
    if (!plan || !allDependenciesSelected) return;
    setError(null);
    try {
      await actions.stage.mutateAsync({
        sourceId,
        editType: "delete",
        targetIri: plan.targetIri,
        targetLabel: plan.targetLabel,
        dependencyKeys: [...selectedKeys],
        idempotencyKey: `web-delete-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      });
      onClose();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The deletion could not be staged.");
    }
  }

  return <section className="edit-dialog deletion-dialog" role="dialog" aria-modal="true" aria-labelledby="deletion-heading">
    <DialogHeader eyebrow="Deletion review" title={`Delete ${entity.label}`} headingId="deletion-heading" onClose={onClose} />
    <p className="edit-dialog-description">Review every statement that will be removed. The source remains unchanged until the proposal is accepted and applied.</p>
    {!plan && !error ? <p role="status">Inspecting dependencies...</p> : null}
    {plan ? <>
      <DependencyGroup title="Entity statements" dependencies={plan.directStatements} />
      <fieldset className="dependency-selection"><legend>Dependent statements</legend>
        {plan.dependentStatements.length ? plan.dependentStatements.map((dependency) => <label className="dependency-option" key={dependency.key}>
          <input
            type="checkbox"
            checked={selectedKeys.has(dependency.key)}
            onChange={(event) => setSelectedKeys((current) => {
              const next = new Set(current);
              if (event.target.checked) next.add(dependency.key); else next.delete(dependency.key);
              return next;
            })}
          />
          <span><strong>{displayDependencyKind(dependency.kind)}</strong><small>{dependency.subjectLabel} · {dependency.predicateLabel} · {dependency.objectLabel}</small></span>
        </label>) : <p className="muted">No other statements depend on this entity.</p>}
      </fieldset>
      {!allDependenciesSelected ? <p className="deletion-guidance">Select every dependent statement to stage a safe deletion.</p> : null}
    </> : null}
    {error ? <p className="workflow-error" role="alert">{error}</p> : null}
    <div className="dialog-actions"><button className="button" type="button" onClick={onClose}>Cancel</button><button className="button danger" type="button" disabled={!allDependenciesSelected || actions.stage.isPending} onClick={stageDeletion}>{actions.stage.isPending ? "Staging…" : "Stage deletion"}</button></div>
  </section>;
}

function DependencyGroup({ title, dependencies }: { title: string; dependencies: WebDeletionDependenciesResponse["directStatements"] }) {
  return <section className="dependency-group"><h3>{title}</h3><ul>{dependencies.map((dependency) => <li key={dependency.key}><strong>{displayDependencyKind(dependency.kind)}</strong><span>{dependency.subjectLabel} · {dependency.predicateLabel} · {dependency.objectLabel}</span></li>)}</ul></section>;
}

function displayDependencyKind(kind: string): string {
  return kind.replace(/([a-z])([A-Z])/g, "$1 $2");
}

function DialogHeader({ eyebrow, title, headingId, onClose }: { eyebrow: string; title: string; headingId: string; onClose: () => void }) {
  return <header className="edit-dialog-header"><div><span className="overline">{eyebrow}</span><h2 id={headingId}>{title}</h2></div><button className="icon-button" type="button" aria-label="Close edit dialog" onClick={onClose}>×</button></header>;
}

interface PropertyAssociation {
  id: string;
  propertyLabel: string;
  relation: "domain" | "range";
}
