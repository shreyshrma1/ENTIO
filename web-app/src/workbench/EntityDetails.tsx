import { useEffect, useMemo, useRef, useState } from "react";
import { useEntityDetails, useProjectSources, useShaclShapes, useStagedChanges, useStagingActions } from "../web/queries";
import type {
  WebEntityDetailResponse,
  WebEntityReference,
  WebRdfValue,
  WebTextValue,
  WebRelationship,
  WebStageChangeRequest,
  WebStagedEntry,
  WebShaclConstraintSummary,
  WebShaclPropertyShapeSummary,
  WebShaclShapeSummary,
} from "../web/projectApi";
import StatusBadge from "../components/ui/StatusBadge";
import SemanticClassPicker, { type SemanticClassChoice } from "./SemanticClassPicker";
import SemanticEntityPicker, { type SemanticEntityChoice } from "./SemanticEntityPicker";

interface EntityDetailsProps {
  projectId: string;
  iri: string;
  stagedEntity?: WebEntityDetailResponse;
  stagedEntries?: WebStagedEntry[];
  directType?: WebEntityReference | null;
  initialSection?: EntitySectionTarget;
  sectionRequestId?: number;
  onOpenEntity?: (entity: WebEntityReference, section?: EntitySectionTarget) => void;
  includeAppliedInferred?: boolean;
  includeProposalInferred?: boolean;
}

export type EntitySectionTarget = "overview" | "shacl";
type EditorSectionId = "overview" | "hierarchy" | "properties" | "schema" | "relationships" | "shacl";
type ClassPropertyDirection = "outgoing" | "incoming" | "datatype";
type StagedField = "preferredLabel" | "definition" | "alternateLabel" | "types" | "superclasses" | "subclasses" | "domains" | "ranges" | "properties" | "relationships" | "shacl";

export default function EntityDetails({ projectId, iri, stagedEntity, stagedEntries = [], directType, initialSection, sectionRequestId, onOpenEntity, includeAppliedInferred = false, includeProposalInferred = false }: EntityDetailsProps) {
  const details = useEntityDetails(projectId, iri, !stagedEntity, includeAppliedInferred, includeProposalInferred);
  const sourceEntity = stagedEntity ?? details.data;
  const entity = useMemo(
    () => sourceEntity ? mergeStagedEntity(sourceEntity, stagedEntries) : undefined,
    [sourceEntity, stagedEntries],
  );
  const stagedFields = useMemo(() => entity ? stagedEntityFields(entity.iri, stagedEntries) : new Set<StagedField>(), [entity, stagedEntries]);

  if (!stagedEntity && details.isPending) return <p role="status">Loading entity details...</p>;
  if (!stagedEntity && details.isError) return <p role="alert">Could not load this entity. {details.error.message}</p>;
  if (!entity) return <p role="alert">Could not load this entity.</p>;

  return (
    <article className="entity-details">
      <div className="entity-heading">
        <div>
          <p className="eyebrow">{entity.kind}</p>
          <h2>{entity.label}</h2>
        </div>
        <div className="entity-statuses">
          <StatusBadge tone={stagedEntity ? "staged" : entity.locality === "External" ? "external" : "asserted"}>{stagedEntity ? "Staged" : entity.locality}</StatusBadge>
          {!stagedEntity && details.isFetching ? <StatusBadge tone="neutral">Refreshing</StatusBadge> : null}
        </div>
      </div>
      <p className="entity-meta">Source: {entity.sourceId} · {stagedEntity ? "pending proposal review" : entity.locality.toLowerCase()}</p>
      <InferredEntityFacts entity={entity} onOpenEntity={onOpenEntity} />
      {entity.locality !== "External"
        ? <EntityDetailWorkspace projectId={projectId} entity={entity} appliedEntity={sourceEntity ?? entity} stagedFields={stagedFields} directType={directType} initialSection={initialSection} sectionRequestId={sectionRequestId} onOpenEntity={onOpenEntity} />
        : <ExternalEntityOverview entity={entity} />}
    </article>
  );
}

function InferredEntityFacts({ entity, onOpenEntity }: {
  entity: WebEntityDetailResponse;
  onOpenEntity?: (entity: WebEntityReference) => void;
}) {
  const overlays = entity.inferredOverlays ?? [];
  const facts = overlays.flatMap((overlay) => overlay.facts.map((fact) => ({ fact, state: overlay.graphState })))
    .filter(({ fact }) => fact.subject === entity.iri || fact.objectValue === entity.iri);
  const notices = overlays.filter((overlay) => overlay.state !== "Off" && overlay.state !== "Current");
  if (!facts.length && !notices.length) return null;
  return <section className="inferred-facts-panel" aria-label="Inferred facts">
    <h3>Inferred facts</h3>
    {notices.map((overlay) => <p className="inferred-state-message" role="status" key={overlay.graphState}>
      <strong>{overlay.graphState}:</strong> {overlay.state === "Updating" ? "Reasoning is updating." : overlay.message ?? `Inferred facts are ${overlay.state.toLowerCase()}.`}
    </p>)}
    {facts.length ? <ul>{facts.map(({ fact, state }) => {
      const otherIri = fact.subject === entity.iri ? fact.objectValue : fact.subject;
      const label = technicalLabel(otherIri);
      return <li className={`inferred-fact inferred-fact-${state.toLowerCase()}`} key={`${state}:${fact.semanticFactKey}`}>
        <span><strong>{inferredKindLabel(fact.kind)}:</strong> </span>
        <button type="button" className="inline-entity-link" onClick={() => onOpenEntity?.({ iri: otherIri, label, kind: null, sourceId: fact.sourceId })}>{label}</button>
        <span className="inferred-badge">Inferred · {state}</span>
      </li>;
    })}</ul> : null}
  </section>;
}

function inferredKindLabel(kind: string): string {
  return {
    SubclassRelationship: "Class hierarchy",
    IndividualType: "Type",
    ObjectPropertyAssertion: "Relationship",
    EffectiveDomain: "Effective domain",
    EffectiveRange: "Effective range",
  }[kind] ?? "Relationship";
}

function technicalLabel(iri: string): string {
  try {
    return decodeURIComponent(iri.split(/[\/#]/).filter(Boolean).at(-1) ?? iri);
  } catch {
    return iri;
  }
}

function EntityDetailWorkspace({ projectId, entity, appliedEntity, stagedFields, directType, initialSection, sectionRequestId, onOpenEntity }: {
  projectId: string;
  entity: WebEntityDetailResponse;
  appliedEntity: WebEntityDetailResponse;
  stagedFields: ReadonlySet<StagedField>;
  directType?: WebEntityReference | null;
  initialSection?: EntitySectionTarget;
  sectionRequestId?: number;
  onOpenEntity?: (entity: WebEntityReference, section?: EntitySectionTarget) => void;
}) {
  const actions = useStagingActions(projectId);
  const stagedChanges = useStagedChanges(projectId);
  const sources = useProjectSources(projectId);
  const [activeSection, setActiveSection] = useState<EditorSectionId>("overview");
  const [preferredLabel, setPreferredLabel] = useState(entity.label);
  const [definition, setDefinition] = useState(entity.definitions[0]?.value ?? "");
  const [alternateLabel, setAlternateLabel] = useState(entity.alternateLabels[0]?.value ?? "");
  const [superclasses, setSuperclasses] = useState<SemanticClassChoice[]>(() => entity.directSuperclasses.slice(0, 1).map(classChoice));
  const [subclass, setSubclass] = useState<SemanticClassChoice[]>(() => entity.directSubclasses.map(classChoice));
  const [type, setType] = useState<SemanticClassChoice[]>(() => entity.assertedTypes.map(classChoice));
  const [domain, setDomain] = useState<SemanticClassChoice[]>(() => entity.domains[0] ? [classChoice(entity.domains[0])] : []);
  const [range, setRange] = useState<SemanticClassChoice[]>(() => entity.ranges[0] ? [classChoice(entity.ranges[0])] : []);
  const [datatypeRange, setDatatypeRange] = useState(entity.ranges[0]?.label ?? "string");
  const [outgoingProperty, setOutgoingProperty] = useState<SemanticEntityChoice[]>([]);
  const [outgoingObject, setOutgoingObject] = useState<SemanticEntityChoice[]>([]);
  const [datatypeProperty, setDatatypeProperty] = useState<SemanticEntityChoice[]>([]);
  const [literalValue, setLiteralValue] = useState("");
  const [literalDatatype, setLiteralDatatype] = useState(XSD_STRING);
  const [incomingSubject, setIncomingSubject] = useState<SemanticEntityChoice[]>([]);
  const [incomingProperty, setIncomingProperty] = useState<SemanticEntityChoice[]>([]);
  const [shapeLabel, setShapeLabel] = useState(`${entity.label} constraint`);
  const [shaclTarget, setShaclTarget] = useState<SemanticClassChoice[]>(() => initialShaclTarget(entity));
  const [shaclPath, setShaclPath] = useState<SemanticEntityChoice[]>(() => initialShaclPath(entity));
  const [constraintKind, setConstraintKind] = useState("min-count");
  const [constraintValue, setConstraintValue] = useState("1");
  const [severity, setSeverity] = useState("Violation");
  const [validationMessage, setValidationMessage] = useState("");
  const [propertyDialog, setPropertyDialog] = useState<ClassPropertyDirection | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const stagedEntries = useRef<WebStagedEntry[]>([]);
  const autoStageTimers = useRef(new Map<string, number>());
  const autoStageQueue = useRef<Promise<void>>(Promise.resolve());

  const kind = entity.kind.toLocaleLowerCase().replaceAll(" ", "");
  const isClass = kind === "class";
  const isProperty = kind.endsWith("property");
  const isObject = !isClass && !isProperty;
  const isDatatypeProperty = kind === "datatypeproperty";
  const shapesSourceId = (sources.data?.items ?? []).find((source) => source.roles.some((role) => role.toLocaleLowerCase() === "shapes"))?.id;

  useEffect(() => {
    const entries = stagedChanges.data?.entries ?? [];
    stagedEntries.current = entries;
    const stagedTypes = entries
      .filter((entry) => entry.editType === "assign-type" && entry.normalizedValues.resourceIri === entity.iri)
      .map((entry) => stagedChoice(entry, "typeIri", "typeLabel", "Class"));
    setType(mergeChoices(appliedEntity.assertedTypes.map(classChoice), stagedTypes));

    const removedSuperclasses = new Set(entries
      .filter((entry) => entry.editType === "remove-superclass" && entry.normalizedValues.classIri === entity.iri)
      .map((entry) => entry.normalizedValues.superclassIri));
    const stagedSuperclasses = entries
      .filter((entry) => entry.editType === "add-superclass" && entry.normalizedValues.classIri === entity.iri)
      .map((entry) => stagedChoice(entry, "superclassIri", "superclassLabel", "Class"));
    setSuperclasses(mergeChoices(
      appliedEntity.directSuperclasses.filter((item) => !removedSuperclasses.has(item.iri)).map(classChoice),
      stagedSuperclasses,
    ).slice(-1));

    const removedSubclasses = new Set(entries
      .filter((entry) => entry.editType === "remove-superclass" && entry.normalizedValues.superclassIri === entity.iri)
      .map((entry) => entry.normalizedValues.classIri));
    const stagedSubclasses = entries
      .filter((entry) => entry.editType === "add-superclass" && entry.normalizedValues.superclassIri === entity.iri)
      .map((entry) => stagedChoice(entry, "classIri", "classLabel", "Class"));
    setSubclass(mergeChoices(
      appliedEntity.directSubclasses.filter((item) => !removedSubclasses.has(item.iri)).map(classChoice),
      stagedSubclasses,
    ));
  }, [appliedEntity, entity, stagedChanges.data?.entries]);

  useEffect(() => () => {
    autoStageTimers.current.forEach((timer) => window.clearTimeout(timer));
    autoStageTimers.current.clear();
  }, []);

  useEffect(() => {
    setActiveSection(initialSection ?? "overview");
  }, [entity.iri, initialSection, sectionRequestId]);

  useEffect(() => {
    setPreferredLabel(entity.label);
    setDefinition(entity.definitions[0]?.value ?? "");
    setAlternateLabel(entity.alternateLabels[0]?.value ?? "");
    setSuperclasses(entity.directSuperclasses.slice(0, 1).map(classChoice));
    setSubclass(entity.directSubclasses.map(classChoice));
    setType(entity.assertedTypes.map(classChoice));
    setDomain(entity.domains[0] ? [classChoice(entity.domains[0])] : []);
    setRange(entity.ranges[0] ? [classChoice(entity.ranges[0])] : []);
    setDatatypeRange(entity.ranges[0]?.label ?? "string");
    setOutgoingProperty([]);
    setOutgoingObject([]);
    setDatatypeProperty([]);
    setLiteralValue("");
    setLiteralDatatype(XSD_STRING);
    setIncomingSubject([]);
    setIncomingProperty([]);
    setShapeLabel(`${entity.label} constraint`);
    setShaclTarget(initialShaclTarget(entity));
    setShaclPath(initialShaclPath(entity));
    setConstraintKind("min-count");
    setConstraintValue("1");
    setSeverity("Violation");
    setValidationMessage("");
    setPropertyDialog(null);
    setMessage(null);
    setError(null);
  }, [entity]);

  function scheduleAutoChange(
    slot: string,
    request: Omit<WebStageChangeRequest, "sourceId"> | null,
    matches: (entry: WebStagedEntry) => boolean,
    successMessage: string,
    delay = 450,
    sourceId = entity.sourceId,
    onSynchronized?: () => void,
  ) {
    const previousTimer = autoStageTimers.current.get(slot);
    if (previousTimer !== undefined) window.clearTimeout(previousTimer);
    const timer = window.setTimeout(() => {
      autoStageQueue.current = autoStageQueue.current.then(async () => {
        setMessage(null);
        setError(null);
        const existing = stagedEntries.current.find(matches);
        try {
          if (!request) {
            if (!existing) return;
            const response = await actions.discard.mutateAsync(existing.id);
            stagedEntries.current = response.entries;
            setMessage("The field matches its applied value, so its staged change was removed.");
            return;
          }
          const response = await actions.stage.mutateAsync({
            ...request,
            sourceId,
            replacesStagedId: existing?.id,
            idempotencyKey: `web-auto-${slot}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
          });
          stagedEntries.current = response.entries;
          setMessage(successMessage);
          onSynchronized?.();
        } catch (failure) {
          setError(failure instanceof Error ? failure.message : "The field change could not be synchronized with the review queue.");
        }
      });
    }, delay);
    autoStageTimers.current.set(slot, timer);
  }

  function changePreferredLabel(value: string) {
    setPreferredLabel(value);
    const label = value.trim();
    scheduleAutoChange(
      "preferred-label",
      label && label !== appliedEntity.label
        ? { editType: "set-entity-label", resourceIri: entity.iri, resourceLabel: appliedEntity.label, label }
        : null,
      (entry) => entry.editType === "set-entity-label" && entry.normalizedValues.resourceIri === entity.iri,
      "Preferred label synchronized with the review queue.",
    );
  }

  function changeDefinition(value: string) {
    setDefinition(value);
    const original = appliedEntity.definitions[0]?.value ?? "";
    const next = value.trim();
    const request = next === original
      ? null
      : original && next
        ? { editType: "replace-definition", targetIri: entity.iri, targetLabel: entity.label, existingValue: original, value: next }
        : original
          ? { editType: "remove-definition", targetIri: entity.iri, targetLabel: entity.label, value: original }
          : next
            ? { editType: "add-definition", targetIri: entity.iri, targetLabel: entity.label, value: next }
            : null;
    scheduleAutoChange(
      "definition",
      request,
      (entry) => ["add-definition", "replace-definition", "remove-definition"].includes(entry.editType) && entry.normalizedValues.targetIri === entity.iri,
      "Definition synchronized with the review queue.",
    );
  }

  function changeAlternateLabel(value: string) {
    setAlternateLabel(value);
    const original = appliedEntity.alternateLabels[0]?.value ?? "";
    const next = value.trim();
    const request = next === original
      ? null
      : original && next
        ? { editType: "replace-alternate-label", targetIri: entity.iri, targetLabel: entity.label, existingValue: original, value: next }
        : original
          ? { editType: "remove-alternate-label", targetIri: entity.iri, targetLabel: entity.label, value: original }
          : next
            ? { editType: "add-alternate-label", targetIri: entity.iri, targetLabel: entity.label, value: next }
            : null;
    scheduleAutoChange(
      "alternate-label",
      request,
      (entry) => ["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(entry.editType) && entry.normalizedValues.targetIri === entity.iri,
      "Alternate label synchronized with the review queue.",
    );
  }

  async function synchronizeOperations(
    slot: string,
    desired: Array<Omit<WebStageChangeRequest, "sourceId">>,
    matches: (entry: WebStagedEntry) => boolean,
    operationKey: (request: Omit<WebStageChangeRequest, "sourceId">) => string,
  ) {
    autoStageQueue.current = autoStageQueue.current.then(async () => {
      setMessage(null);
      setError(null);
      try {
        let responseEntries = stagedEntries.current;
        const existing = responseEntries.filter(matches);
        const desiredByKey = new Map(desired.map((request) => [operationKey(request), request]));
        for (const entry of existing) {
          if (!desiredByKey.has(stagedEntryKey(entry))) {
            const response = await actions.discard.mutateAsync(entry.id);
            responseEntries = response.entries;
          }
        }
        const retainedKeys = new Set(responseEntries.filter(matches).map(stagedEntryKey));
        for (const [key, request] of desiredByKey) {
          if (retainedKeys.has(key)) continue;
          const response = await actions.stage.mutateAsync({
            ...request,
            sourceId: entity.sourceId,
            idempotencyKey: `web-auto-${slot}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
          });
          responseEntries = response.entries;
        }
        stagedEntries.current = responseEntries;
        const remainingCount = responseEntries.length;
        setMessage(desired.length
          ? "Changes synchronized with the review queue."
          : remainingCount
            ? `This field's staged changes were removed. ${remainingCount} other staged change${remainingCount === 1 ? "" : "s"} remain${remainingCount === 1 ? "s" : ""}.`
            : "This field's staged changes were removed. The review queue is now empty.");
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : "The field changes could not be synchronized with the review queue.");
      }
    });
    await autoStageQueue.current;
  }

  async function stage(request: Omit<WebStageChangeRequest, "sourceId">, successMessage: string, sourceId = entity.sourceId) {
    setMessage(null);
    setError(null);
    try {
      await actions.stage.mutateAsync({
        ...request,
        sourceId,
        idempotencyKey: `web-details-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      });
      setMessage(successMessage);
      return true;
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The detail change could not be staged.");
      return false;
    }
  }

  async function stageClassProperty(
    label: string,
    domainClass: SemanticClassChoice,
    range: SemanticClassChoice | { iri: string; label: string },
    propertyKind: "object" | "datatype",
  ) {
    setMessage(null);
    setError(null);
    const batchKey = `web-class-property-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    try {
      const created = await actions.stage.mutateAsync({
        sourceId: entity.sourceId,
        editType: propertyKind === "datatype" ? "create-datatype-property" : "create-object-property",
        label,
        idempotencyKey: `${batchKey}-create`,
      });
      const propertyIri = created.entries.find((entry) => entry.normalizedValues.label === label && entry.generatedIris.length)?.generatedIris[0];
      if (!propertyIri) throw new Error("Entio did not return the generated property identifier.");
      await actions.stage.mutateAsync({
        sourceId: entity.sourceId,
        editType: "set-property-domain",
        propertyIri,
        propertyLabel: label,
        domainClassIri: domainClass.iri,
        domainClassLabel: domainClass.label,
        idempotencyKey: `${batchKey}-domain`,
      });
      await actions.stage.mutateAsync({
        sourceId: entity.sourceId,
        editType: "set-property-range",
        propertyIri,
        propertyLabel: label,
        rangeIri: range.iri,
        rangeLabel: range.label,
        idempotencyKey: `${batchKey}-range`,
      });
      setPropertyDialog(null);
      setMessage(`${label} staged with its domain and range.`);
    } catch (failure) {
      setError(`${failure instanceof Error ? failure.message : "The property could not be staged."} Any completed items remain visible in the review queue.`);
    }
  }

  function changeDomain(next: SemanticClassChoice[]) {
    setDomain(next);
    const applied = appliedEntity.domains[0];
    const selected = next[0];
    const desired: Array<Omit<WebStageChangeRequest, "sourceId">> = [];
    if (applied && applied.iri !== selected?.iri) desired.push({ editType: "remove-property-domain", propertyIri: entity.iri, propertyLabel: entity.label, domainClassIri: applied.iri, domainClassLabel: applied.label });
    if (selected && selected.iri !== applied?.iri) desired.push({ editType: "set-property-domain", propertyIri: entity.iri, propertyLabel: entity.label, domainClassIri: selected.iri, domainClassLabel: selected.label });
    void synchronizeOperations(
      "property-domain",
      desired,
      (entry) => ["set-property-domain", "remove-property-domain"].includes(entry.editType) && entry.normalizedValues.propertyIri === entity.iri,
      requestPropertyRelationKey,
    );
  }

  function changeRange(next: SemanticClassChoice[]) {
    setRange(next);
    const applied = appliedEntity.ranges[0];
    const selected = next[0];
    const desired: Array<Omit<WebStageChangeRequest, "sourceId">> = [];
    if (applied && applied.iri !== selected?.iri) desired.push({ editType: "remove-property-range", propertyIri: entity.iri, propertyLabel: entity.label, rangeIri: applied.iri, rangeLabel: applied.label });
    if (selected && selected.iri !== applied?.iri) desired.push({ editType: "set-property-range", propertyIri: entity.iri, propertyLabel: entity.label, rangeIri: selected.iri, rangeLabel: selected.label });
    void synchronizeOperations(
      "property-range",
      desired,
      (entry) => ["set-property-range", "remove-property-range"].includes(entry.editType) && entry.normalizedValues.propertyIri === entity.iri,
      requestPropertyRelationKey,
    );
  }

  function changeDatatypeRange(value: string) {
    setDatatypeRange(value);
    const applied = appliedEntity.ranges[0];
    const desired: Array<Omit<WebStageChangeRequest, "sourceId">> = [];
    if (applied && readableDatatype(applied) !== value) desired.push({ editType: "remove-property-range", propertyIri: entity.iri, propertyLabel: entity.label, rangeIri: applied.iri, rangeLabel: applied.label });
    if (!applied || readableDatatype(applied) !== value) desired.push({ editType: "set-property-range", propertyIri: entity.iri, propertyLabel: entity.label, rangeLabel: value });
    void synchronizeOperations(
      "datatype-range",
      desired,
      (entry) => ["set-property-range", "remove-property-range"].includes(entry.editType) && entry.normalizedValues.propertyIri === entity.iri,
      requestPropertyRelationKey,
    );
  }

  function commitOutgoing() {
    const property = outgoingProperty[0];
    const object = outgoingObject[0];
    if (!property || !object) return;
    scheduleAutoChange(
      `outgoing-relationship-${property.iri}-${object.iri}`,
      { editType: "add-object-property-assertion", subjectIri: entity.iri, subjectLabel: entity.label, propertyIri: property.iri, propertyLabel: property.label, objectIri: object.iri, objectLabel: object.label },
      (entry) => entry.editType === "add-object-property-assertion" && entry.normalizedValues.subjectIri === entity.iri && entry.normalizedValues.propertyIri === property.iri && entry.normalizedValues.objectIri === object.iri,
      "Outgoing relationship synchronized with the review queue.",
      0,
      entity.sourceId,
      () => { setOutgoingProperty([]); setOutgoingObject([]); },
    );
  }

  function commitDatatypeValue() {
    const property = datatypeProperty[0];
    const value = literalValue.trim();
    if (!property || !value) return;
    scheduleAutoChange(
      `datatype-value-${property.iri}`,
      { editType: "add-datatype-property-assertion", subjectIri: entity.iri, subjectLabel: entity.label, propertyIri: property.iri, propertyLabel: property.label, value, datatypeIri: literalDatatype },
      (entry) => entry.editType === "add-datatype-property-assertion" && entry.normalizedValues.subjectIri === entity.iri && entry.normalizedValues.propertyIri === property.iri,
      "Datatype value synchronized with the review queue.",
      450,
      entity.sourceId,
      () => { setDatatypeProperty([]); setLiteralValue(""); setLiteralDatatype(XSD_STRING); },
    );
  }

  function commitIncoming() {
    const subject = incomingSubject[0];
    const property = incomingProperty[0];
    if (!subject || !property) return;
    scheduleAutoChange(
      `incoming-relationship-${subject.iri}-${property.iri}`,
      { editType: "add-object-property-assertion", subjectIri: subject.iri, subjectLabel: subject.label, propertyIri: property.iri, propertyLabel: property.label, objectIri: entity.iri, objectLabel: entity.label },
      (entry) => entry.editType === "add-object-property-assertion" && entry.normalizedValues.subjectIri === subject.iri && entry.normalizedValues.propertyIri === property.iri && entry.normalizedValues.objectIri === entity.iri,
      "Incoming relationship synchronized with the review queue.",
      0,
      entity.sourceId,
      () => { setIncomingSubject([]); setIncomingProperty([]); },
    );
  }

  return <section className="entity-tab-workspace" aria-label={`${entity.label} details and editing`}>
    <div className="entity-editor-tabs" role="tablist" aria-label="Entity detail sections">
      <EditorTab id="overview" label="Overview" active={activeSection} onSelect={setActiveSection} />
      {!isProperty ? <EditorTab id="hierarchy" label="Hierarchy" active={activeSection} onSelect={setActiveSection} /> : null}
      {isClass ? <EditorTab id="properties" label="Properties" active={activeSection} onSelect={setActiveSection} /> : null}
      {isProperty ? <EditorTab id="schema" label="Schema" active={activeSection} onSelect={setActiveSection} /> : null}
      {isObject ? <EditorTab id="relationships" label="Relationships" active={activeSection} onSelect={setActiveSection} /> : null}
      <EditorTab id="shacl" label={isClass ? "Constraints" : isProperty ? "Constraint usage" : "Validation"} active={activeSection} onSelect={setActiveSection} />
    </div>

    <div className="entity-tab-panel" role="tabpanel">
      {activeSection === "overview" ? <OverviewTab
        entity={entity}
        stagedFields={stagedFields}
        preferredLabel={preferredLabel}
        definition={definition}
        alternateLabel={alternateLabel}
        directType={directType}
        onOpenEntity={onOpenEntity}
        onPreferredLabelChange={changePreferredLabel}
        onDefinitionChange={changeDefinition}
        onAlternateLabelChange={changeAlternateLabel}
      /> : null}

      {activeSection === "hierarchy" && !isProperty ? <div className="entity-tab-sections">
        {isObject ? <SemanticListSection title="Types" staged={stagedFields.has("types")}>
          <div className="inline-semantic-editor auto-staged-editor">
            <SemanticClassPicker projectId={projectId} id="entity-type" label="Add asserted type" selected={type} onChange={(next) => {
              setType(next);
              const original = new Set(appliedEntity.assertedTypes.map((item) => item.iri));
              const requests = next
                .filter((item) => !original.has(item.iri))
                .map((item) => ({ editType: "assign-type", resourceIri: entity.iri, resourceLabel: entity.label, typeIri: item.iri, typeLabel: item.label }));
              void synchronizeOperations(
                "asserted-types",
                requests,
                (entry) => entry.editType === "assign-type" && entry.normalizedValues.resourceIri === entity.iri,
                requestTypeKey,
              );
            }} excludeIri={entity.iri} selectionPresentation="list" appliedIris={appliedEntity.assertedTypes.map((item) => item.iri)} removableApplied={false} />
          </div>
        </SemanticListSection> : null}

        {isClass ? <SemanticListSection title="Superclass" staged={stagedFields.has("superclasses")}>
          <div className="inline-semantic-editor auto-staged-editor">
            <SemanticClassPicker projectId={projectId} id="entity-superclasses" label="Direct superclass (one allowed)" selected={superclasses} onChange={(next) => {
              setSuperclasses(next);
              const original = new Map(appliedEntity.directSuperclasses.map((item) => [item.iri, item]));
              const current = new Map(next.map((item) => [item.iri, item]));
              const requests: Array<Omit<WebStageChangeRequest, "sourceId">> = [];
              next.filter((item) => !original.has(item.iri)).forEach((item) => requests.push({ editType: "add-superclass", classIri: entity.iri, classLabel: entity.label, superclassIri: item.iri, superclassLabel: item.label }));
              appliedEntity.directSuperclasses.filter((item) => !current.has(item.iri)).forEach((item) => requests.push({ editType: "remove-superclass", classIri: entity.iri, classLabel: entity.label, superclassIri: item.iri, superclassLabel: item.label }));
              void synchronizeOperations(
                "superclasses",
                requests,
                (entry) => ["add-superclass", "remove-superclass"].includes(entry.editType) && entry.normalizedValues.classIri === entity.iri,
                requestHierarchyKey,
              );
            }} excludeIri={entity.iri} multiple={false} selectionPresentation="list" appliedIris={appliedEntity.directSuperclasses.map((item) => item.iri)} />
          </div>
        </SemanticListSection> : null}

        {isClass ? <SemanticListSection title="Subclasses" staged={stagedFields.has("subclasses")}>
          <div className="inline-semantic-editor auto-staged-editor">
            <SemanticClassPicker projectId={projectId} id="entity-subclass" label="Add existing or staged subclass" selected={subclass} onChange={(next) => {
              setSubclass(next);
              const original = new Map(appliedEntity.directSubclasses.map((item) => [item.iri, item]));
              const current = new Map(next.map((item) => [item.iri, item]));
              const requests: Array<Omit<WebStageChangeRequest, "sourceId">> = [];
              next.filter((item) => !original.has(item.iri)).forEach((item) => requests.push({ editType: "add-superclass", classIri: item.iri, classLabel: item.label, superclassIri: entity.iri, superclassLabel: entity.label }));
              appliedEntity.directSubclasses.filter((item) => !current.has(item.iri)).forEach((item) => requests.push({ editType: "remove-superclass", classIri: item.iri, classLabel: item.label, superclassIri: entity.iri, superclassLabel: entity.label }));
              void synchronizeOperations(
                "subclasses",
                requests,
                (entry) => ["add-superclass", "remove-superclass"].includes(entry.editType) && entry.normalizedValues.superclassIri === entity.iri && entry.normalizedValues.classIri !== entity.iri,
                requestHierarchyKey,
              );
            }} excludeIri={entity.iri} selectionPresentation="list" appliedIris={appliedEntity.directSubclasses.map((item) => item.iri)} />
          </div>
        </SemanticListSection> : null}

      </div> : null}

      {activeSection === "schema" ? <SchemaTab
        projectId={projectId}
        entity={entity}
        stagedFields={stagedFields}
        isProperty={isProperty}
        isDatatypeProperty={isDatatypeProperty}
        domain={domain}
        range={range}
        datatypeRange={datatypeRange}
        onDomainChange={changeDomain}
        onRangeChange={changeRange}
        onDatatypeRangeChange={changeDatatypeRange}
      /> : null}

      {activeSection === "properties" && isClass ? <ClassPropertiesTab
        entity={entity}
        stagedFields={stagedFields}
        stagedEntries={stagedChanges.data?.entries ?? []}
        pending={actions.stage.isPending}
        onAdd={setPropertyDialog}
        onDiscard={(entryId) => actions.discard.mutateAsync(entryId).then(() => undefined)}
        onRemove={(property, direction) => void stage(direction === "outgoing" || direction === "datatype" ? {
          editType: "remove-property-domain",
          propertyIri: property.iri,
          propertyLabel: property.label,
          domainClassIri: entity.iri,
          domainClassLabel: entity.label,
        } : {
          editType: "remove-property-range",
          propertyIri: property.iri,
          propertyLabel: property.label,
          rangeIri: entity.iri,
          rangeLabel: entity.label,
        }, `${property.label} ${direction === "outgoing" || direction === "datatype" ? "domain" : "range"} association staged for removal.`, property.sourceId ?? entity.sourceId)}
      /> : null}

      {activeSection === "relationships" ? <RelationshipsTab
        projectId={projectId}
        entity={entity}
        editable={isObject}
        outgoingProperty={outgoingProperty}
        outgoingObject={outgoingObject}
        datatypeProperty={datatypeProperty}
        literalValue={literalValue}
        literalDatatype={literalDatatype}
        incomingSubject={incomingSubject}
        incomingProperty={incomingProperty}
        onOutgoingPropertyChange={setOutgoingProperty}
        onOutgoingObjectChange={setOutgoingObject}
        onDatatypePropertyChange={setDatatypeProperty}
        onLiteralValueChange={setLiteralValue}
        onLiteralDatatypeChange={setLiteralDatatype}
        onIncomingSubjectChange={setIncomingSubject}
        onIncomingPropertyChange={setIncomingProperty}
        onCommitOutgoing={commitOutgoing}
        onCommitDatatype={commitDatatypeValue}
        onCommitIncoming={commitIncoming}
        stagedEntries={stagedChanges.data?.entries ?? []}
        stagedFields={stagedFields}
        onDiscard={(entryId) => actions.discard.mutateAsync(entryId).then(() => undefined)}
      /> : null}

      {activeSection === "shacl" ? <ShaclTab
        projectId={projectId}
        entity={entity}
        shapesSourceId={shapesSourceId}
        shapeLabel={shapeLabel}
        target={shaclTarget}
        path={shaclPath}
        constraintKind={constraintKind}
        constraintValue={constraintValue}
        severity={severity}
        validationMessage={validationMessage}
        stagedFields={stagedFields}
        pending={actions.stage.isPending}
        onShapeLabelChange={setShapeLabel}
        onTargetChange={setShaclTarget}
        onPathChange={setShaclPath}
        onConstraintKindChange={setConstraintKind}
        onConstraintValueChange={setConstraintValue}
        onSeverityChange={setSeverity}
        onValidationMessageChange={setValidationMessage}
        onOpenEntity={onOpenEntity}
        onStage={stage}
      /> : null}
    </div>

    {message ? <p className="entity-edit-message" role="status">{message}</p> : null}
    {error ? <p className="workflow-error" role="alert">{error}</p> : null}
    {propertyDialog ? <ClassPropertyDialog
      projectId={projectId}
      entity={entity}
      direction={propertyDialog}
      pending={actions.stage.isPending}
      onClose={() => setPropertyDialog(null)}
      onSubmit={stageClassProperty}
    /> : null}
  </section>;
}

interface OverviewTabProps {
  entity: WebEntityDetailResponse;
  stagedFields: ReadonlySet<StagedField>;
  preferredLabel: string;
  definition: string;
  alternateLabel: string;
  directType?: WebEntityReference | null;
  onOpenEntity?: (entity: WebEntityReference, section?: EntitySectionTarget) => void;
  onPreferredLabelChange: (value: string) => void;
  onDefinitionChange: (value: string) => void;
  onAlternateLabelChange: (value: string) => void;
}

function OverviewTab(props: OverviewTabProps) {
  const kind = props.entity.kind.toLowerCase().replaceAll(" ", "");
  const isClass = kind === "class";
  const isProperty = kind.endsWith("property");
  const directType = props.directType ?? props.entity.assertedTypes.find((type) => !isBuiltInIndividualType(type.iri)) ?? null;
  return <div className="entity-tab-sections">
    <EditableFactSection title="Preferred label" values={[props.entity.label]} staged={props.stagedFields.has("preferredLabel")}>
      <div className="inline-value-editor auto-staged-editor">
        <label className="visually-hidden" htmlFor="entity-preferred-label">Preferred label</label>
        <input id="entity-preferred-label" value={props.preferredLabel} onChange={(event) => props.onPreferredLabelChange(event.target.value)} />
      </div>
    </EditableFactSection>
    <EditableFactSection title="Definitions" values={props.entity.definitions.map((item) => item.value)} staged={props.stagedFields.has("definition")}>
      <div className="inline-value-editor auto-staged-editor">
        <label className="visually-hidden" htmlFor="entity-definition">Definition</label>
        <textarea id="entity-definition" value={props.definition} onChange={(event) => props.onDefinitionChange(event.target.value)} placeholder="Add a human-readable definition" rows={2} />
      </div>
    </EditableFactSection>
    <EditableFactSection title="Alternate labels" values={props.entity.alternateLabels.map((item) => item.value)} staged={props.stagedFields.has("alternateLabel")}>
      <div className="inline-value-editor auto-staged-editor">
        <label className="visually-hidden" htmlFor="entity-alternate-label">Alternate label</label>
        <input id="entity-alternate-label" value={props.alternateLabel} onChange={(event) => props.onAlternateLabelChange(event.target.value)} placeholder="Add an alternate label" />
      </div>
    </EditableFactSection>
    <ReadOnlyFactSection title="Annotations" values={props.entity.annotations.map((item) => `${item.property.label}: ${formatValue(item.value)}`)} />
    {isClass ? <ReferenceFactSection title="Direct objects" items={props.entity.directlyTypedIndividuals} onOpen={props.onOpenEntity} /> : null}
    {!isClass && !isProperty ? <ReferenceFactSection title="Direct class type" items={directType ? [directType] : []} onOpen={props.onOpenEntity} /> : null}
    <details className="technical-details">
      <summary>Technical details</summary>
      <dl>
        <div><dt>IRI</dt><dd><code>{props.entity.iri}</code></dd></div>
        <div><dt>Preferred label source</dt><dd>{props.entity.preferredLabelSource}</dd></div>
        {props.entity.sourceOntologyId ? <div><dt>Source ontology</dt><dd>{props.entity.sourceOntologyId}</dd></div> : null}
      </dl>
    </details>
  </div>;
}

function ReferenceFactSection({ title, items, onOpen }: {
  title: string;
  items: WebEntityReference[];
  onOpen?: (entity: WebEntityReference, section?: EntitySectionTarget) => void;
}) {
  return <section className="editable-fact-section entity-reference-section">
    <div className="editable-fact-summary"><strong>{title}</strong></div>
    {items.length ? <ul className="entity-reference-list">{items.map((item) => <li key={item.iri}>
      {onOpen ? <button type="button" className="entity-reference-button" onClick={() => onOpen(item)}>{item.label}</button> : <span className="entity-reference-value">{item.label}</span>}
    </li>)}</ul> : <p className="fact-empty">N/A</p>}
  </section>;
}

function ReadOnlyFactSection({ title, values }: { title: string; values: string[] }) {
  return <section className="editable-fact-section entity-reference-section">
    <div className="editable-fact-summary"><strong>{title}</strong></div>
    {values.length ? <ul className="entity-reference-list">{values.map((value, index) => <li key={`${value}:${index}`}>
      <span className="entity-reference-value">{value}</span>
    </li>)}</ul> : <p className="fact-empty">N/A</p>}
  </section>;
}

function isBuiltInIndividualType(iri: string): boolean {
  return iri === "http://www.w3.org/2002/07/owl#NamedIndividual"
    || iri === "http://www.w3.org/2000/01/rdf-schema#Resource";
}

function ClassPropertiesTab({
  entity,
  stagedFields,
  stagedEntries,
  pending,
  onAdd,
  onDiscard,
  onRemove,
}: {
  entity: WebEntityDetailResponse;
  stagedFields: ReadonlySet<StagedField>;
  stagedEntries: WebStagedEntry[];
  pending: boolean;
  onAdd: (direction: ClassPropertyDirection) => void;
  onDiscard: (entryId: string) => Promise<void>;
  onRemove: (property: WebEntityReference, direction: ClassPropertyDirection) => void;
}) {
  const [activePropertyKind, setActivePropertyKind] = useState<ClassPropertyDirection>("outgoing");
  const properties = classPropertyRows(entity, stagedEntries, activePropertyKind);
  const copy = CLASS_PROPERTY_SECTIONS[activePropertyKind];
  return <div className={`relationship-workspace class-properties-workspace ${stagedFields.has("properties") ? "staged-field-group" : ""}`}>
    <div className="relationship-subtabs" role="tablist" aria-label="Class property kinds">
      <EditorTab id="outgoing" label="Outgoing" active={activePropertyKind} onSelect={setActivePropertyKind} />
      <EditorTab id="incoming" label="Incoming" active={activePropertyKind} onSelect={setActivePropertyKind} />
      <EditorTab id="datatype" label="Datatype" active={activePropertyKind} onSelect={setActivePropertyKind} />
    </div>
    <div className="relationship-subtab-panel" role="tabpanel">
      <ClassPropertySection
        title={copy.title}
        description={copy.description(entity.label)}
        empty={copy.empty}
        properties={properties}
        pending={pending}
        onAdd={() => onAdd(activePropertyKind)}
        onRemove={(property) => property.stagedId ? void onDiscard(property.stagedId) : onRemove(property, activePropertyKind)}
      />
    </div>
  </div>;
}

function ClassPropertySection({
  title,
  description,
  empty,
  properties,
  pending,
  onAdd,
  onRemove,
}: {
  title: string;
  description: string;
  empty: string;
  properties: ClassPropertyRow[];
  pending: boolean;
  onAdd: () => void;
  onRemove: (property: ClassPropertyRow) => void;
}) {
  return <section className="class-property-section">
    <header>
      <div><h3>{title}</h3><p>{description}</p></div>
      <button className="button small" type="button" onClick={onAdd}>Add property</button>
    </header>
    {properties.length ? <ul className="class-property-list">
      {properties.map((property) => <li className={property.staged ? "class-property-staged" : undefined} key={`${property.iri}:${property.stagedId ?? "applied"}`}>
        <div><strong>{property.label}</strong><span>{property.kind === "DatatypeProperty" ? "Datatype property" : "Object property"}{property.staged ? " · Staged" : ""}</span></div>
        <button className="button small danger" type="button" disabled={pending} onClick={() => onRemove(property)}>Remove</button>
      </li>)}
    </ul> : <p className="class-property-empty">{empty}</p>}
  </section>;
}

function ClassPropertyDialog({
  projectId,
  entity,
  direction,
  pending,
  onClose,
  onSubmit,
}: {
  projectId: string;
  entity: WebEntityDetailResponse;
  direction: ClassPropertyDirection;
  pending: boolean;
  onClose: () => void;
  onSubmit: (label: string, domainClass: SemanticClassChoice, range: SemanticClassChoice | { iri: string; label: string }, propertyKind: "object" | "datatype") => Promise<void>;
}) {
  const currentClass: SemanticClassChoice = { iri: entity.iri, label: entity.label, kind: "Class", sourceId: entity.sourceId, staged: entity.locality.toLocaleLowerCase() === "staged" };
  const [label, setLabel] = useState("");
  const [domain, setDomain] = useState<SemanticClassChoice[]>(direction === "incoming" ? [] : [currentClass]);
  const [range, setRange] = useState<SemanticClassChoice[]>(direction === "incoming" ? [currentClass] : []);
  const [datatypeRange, setDatatypeRange] = useState(XSD_STRING);

  useEffect(() => {
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", escape);
    return () => window.removeEventListener("keydown", escape);
  }, [onClose]);

  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="edit-dialog class-property-dialog" role="dialog" aria-modal="true" aria-labelledby="class-property-dialog-heading">
      <div className="edit-dialog-header">
        <div><p className="eyebrow">Class property</p><h2 id="class-property-dialog-heading">Add {direction} property</h2></div>
        <button className="icon-button" type="button" aria-label="Close property dialog" onClick={onClose}>×</button>
      </div>
      <form onSubmit={(event) => {
        event.preventDefault();
        const propertyLabel = label.trim();
        const selectedRange = direction === "datatype"
          ? { iri: datatypeRange, label: datatypeLabel(datatypeRange) }
          : range[0];
        if (propertyLabel && domain[0] && selectedRange) void onSubmit(propertyLabel, domain[0], selectedRange, direction === "datatype" ? "datatype" : "object");
      }}>
        <SemanticClassPicker projectId={projectId} id="class-property-domain" label="Domain class" selected={domain} onChange={setDomain} multiple={false} />
        <label htmlFor="class-property-label">Property name<input id="class-property-label" autoFocus value={label} onChange={(event) => setLabel(event.target.value)} placeholder="owns account" required /></label>
        {direction === "datatype" ? <label htmlFor="class-property-datatype">Datatype range<select id="class-property-datatype" value={datatypeRange} onChange={(event) => setDatatypeRange(event.target.value)}>{LITERAL_DATATYPES.map((datatype) => <option key={datatype.iri} value={datatype.iri}>{datatype.label}</option>)}</select></label>
          : <SemanticClassPicker projectId={projectId} id="class-property-range" label="Range class" selected={range} onChange={setRange} multiple={false} />}
        <div className="dialog-actions">
          <button className="button" type="button" onClick={onClose}>Cancel</button>
          <button className="button primary" type="submit" disabled={pending || !label.trim() || domain.length === 0 || (direction !== "datatype" && range.length === 0)}>{pending ? "Adding…" : "Add property"}</button>
        </div>
      </form>
    </section>
  </div>;
}

interface SchemaTabProps {
  projectId: string;
  entity: WebEntityDetailResponse;
  stagedFields: ReadonlySet<StagedField>;
  isProperty: boolean;
  isDatatypeProperty: boolean;
  domain: SemanticClassChoice[];
  range: SemanticClassChoice[];
  datatypeRange: string;
  onDomainChange: (value: SemanticClassChoice[]) => void;
  onRangeChange: (value: SemanticClassChoice[]) => void;
  onDatatypeRangeChange: (value: string) => void;
}

function SchemaTab(props: SchemaTabProps) {
  return <div className="entity-tab-sections">
    <EditableFactSection title="Domains" values={props.entity.domains.map((item) => item.label)} staged={props.stagedFields.has("domains")} unavailable={!props.isProperty ? "Domain declarations apply to properties." : undefined}>
      {props.isProperty ? <div className="inline-semantic-editor auto-staged-editor">
        <SemanticClassPicker projectId={props.projectId} id="entity-domain" label="Set domain" selected={props.domain} onChange={props.onDomainChange} multiple={false} />
      </div> : null}
    </EditableFactSection>

    <EditableFactSection title="Ranges" values={props.entity.ranges.map((item) => item.label)} staged={props.stagedFields.has("ranges")} unavailable={!props.isProperty ? "Range declarations apply to properties." : undefined}>
      {props.isProperty && props.isDatatypeProperty ? <div className="inline-value-editor auto-staged-editor">
        <label htmlFor="entity-datatype-range">Datatype range</label>
        <select id="entity-datatype-range" value={props.datatypeRange} onChange={(event) => props.onDatatypeRangeChange(event.target.value)}>
          {STANDARD_DATATYPES.map((datatype) => <option key={datatype} value={datatype}>{datatype}</option>)}
        </select>
      </div> : null}
      {props.isProperty && !props.isDatatypeProperty ? <div className="inline-semantic-editor auto-staged-editor">
        <SemanticClassPicker projectId={props.projectId} id="entity-range" label="Set range" selected={props.range} onChange={props.onRangeChange} multiple={false} />
      </div> : null}
    </EditableFactSection>
  </div>;
}

interface RelationshipsTabProps {
  projectId: string;
  entity: WebEntityDetailResponse;
  stagedFields: ReadonlySet<StagedField>;
  editable: boolean;
  outgoingProperty: SemanticEntityChoice[];
  outgoingObject: SemanticEntityChoice[];
  datatypeProperty: SemanticEntityChoice[];
  literalValue: string;
  literalDatatype: string;
  incomingSubject: SemanticEntityChoice[];
  incomingProperty: SemanticEntityChoice[];
  onOutgoingPropertyChange: (value: SemanticEntityChoice[]) => void;
  onOutgoingObjectChange: (value: SemanticEntityChoice[]) => void;
  onDatatypePropertyChange: (value: SemanticEntityChoice[]) => void;
  onLiteralValueChange: (value: string) => void;
  onLiteralDatatypeChange: (value: string) => void;
  onIncomingSubjectChange: (value: SemanticEntityChoice[]) => void;
  onIncomingPropertyChange: (value: SemanticEntityChoice[]) => void;
  onCommitOutgoing: () => void;
  onCommitDatatype: () => void;
  onCommitIncoming: () => void;
  stagedEntries: WebStagedEntry[];
  onDiscard: (entryId: string) => Promise<void>;
}

function RelationshipsTab(props: RelationshipsTabProps) {
  const [activeRelationship, setActiveRelationship] = useState<"outgoing" | "incoming" | "datatype">("outgoing");
  const rows = relationshipRows(props.entity, props.stagedEntries, activeRelationship);
  return <div className={`relationship-workspace ${props.stagedFields.has("relationships") ? "staged-field-group" : ""}`}>
    <div className="relationship-subtabs" role="tablist" aria-label="Relationship kinds">
      <EditorTab id="outgoing" label="Outgoing" active={activeRelationship} onSelect={setActiveRelationship} />
      <EditorTab id="incoming" label="Incoming" active={activeRelationship} onSelect={setActiveRelationship} />
      <EditorTab id="datatype" label="Datatype" active={activeRelationship} onSelect={setActiveRelationship} />
    </div>
    <div className="relationship-subtab-panel" role="tabpanel">
      {activeRelationship === "outgoing" ? <>
        {props.editable ? <form className="individual-relationship-editor relationship-search-editor" onSubmit={(event) => { event.preventDefault(); props.onCommitOutgoing(); }}>
          <SemanticEntityPicker projectId={props.projectId} id="entity-outgoing-property" label="Object property" selected={props.outgoingProperty} onChange={props.onOutgoingPropertyChange} onCommit={props.onCommitOutgoing} accepts={acceptsObjectProperty} placeholder="Search object properties" help="Choose the relationship predicate." multiple={false} selectedValueInInput selectionPresentation="hidden" appliedIris={[]} />
          <SemanticEntityPicker projectId={props.projectId} id="entity-outgoing-object" label="Object" selected={props.outgoingObject} onChange={props.onOutgoingObjectChange} onCommit={props.onCommitOutgoing} accepts={acceptsIndividual} placeholder="Search individuals" help="Choose the individual that receives this relationship." multiple={false} excludeIri={props.entity.iri} selectedValueInInput selectionPresentation="hidden" appliedIris={[]} />
        </form> : null}
        <RelationshipRows title="Outgoing relationships" empty="No outgoing object relationships." rows={rows} onDiscard={props.onDiscard} />
      </> : null}
      {activeRelationship === "incoming" ? <>
        {props.editable ? <form className="individual-relationship-editor relationship-search-editor" onSubmit={(event) => { event.preventDefault(); props.onCommitIncoming(); }}>
          <SemanticEntityPicker projectId={props.projectId} id="entity-incoming-subject" label="Subject" selected={props.incomingSubject} onChange={props.onIncomingSubjectChange} onCommit={props.onCommitIncoming} accepts={acceptsIndividual} placeholder="Search individuals" help="Choose the individual that points here." multiple={false} excludeIri={props.entity.iri} selectedValueInInput selectionPresentation="hidden" appliedIris={[]} />
          <SemanticEntityPicker projectId={props.projectId} id="entity-incoming-property" label="Object property" selected={props.incomingProperty} onChange={props.onIncomingPropertyChange} onCommit={props.onCommitIncoming} accepts={acceptsObjectProperty} placeholder="Search object properties" help="Choose the relationship predicate." multiple={false} selectedValueInInput selectionPresentation="hidden" appliedIris={[]} />
        </form> : null}
        <RelationshipRows title="Incoming relationships" empty="No incoming object relationships." rows={rows} onDiscard={props.onDiscard} />
      </> : null}
      {activeRelationship === "datatype" ? <>
        {props.editable ? <form className="individual-relationship-editor relationship-search-editor" onSubmit={(event) => { event.preventDefault(); props.onCommitDatatype(); }}>
          <SemanticEntityPicker projectId={props.projectId} id="entity-datatype-property" label="Datatype property" selected={props.datatypeProperty} onChange={props.onDatatypePropertyChange} onCommit={props.onCommitDatatype} accepts={acceptsDatatypeProperty} placeholder="Search datatype properties" help="Choose the literal-valued property." multiple={false} selectedValueInInput selectionPresentation="hidden" appliedIris={[]} />
          <label className="relationship-value-field" htmlFor="entity-literal-value">
            <span>Value</span>
            <span className="typed-literal-control">
              <input
                id="entity-literal-value"
                aria-label="Value"
                type={datatypeInputType(props.literalDatatype)}
                step={isNumericDatatype(props.literalDatatype) ? "any" : undefined}
                value={props.literalValue}
                onChange={(event) => props.onLiteralValueChange(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    props.onCommitDatatype();
                  }
                }}
                placeholder={datatypePlaceholder(props.literalDatatype)}
              />
              <select aria-label="Datatype" value={props.literalDatatype} onChange={(event) => props.onLiteralDatatypeChange(event.target.value)}>
                {LITERAL_DATATYPES.map((datatype) => <option key={datatype.iri} value={datatype.iri}>{datatype.label}</option>)}
              </select>
            </span>
            <small>Enter the lexical value and choose its RDF datatype.</small>
          </label>
        </form> : null}
        <RelationshipRows title="Datatype values" empty="No datatype values." rows={rows} onDiscard={props.onDiscard} />
      </> : null}
    </div>
  </div>;
}

interface RelationshipRow {
  id: string;
  predicate: string;
  value: string;
  staged: boolean;
  stagedId?: string;
  inferredState?: "Applied" | "Proposal";
}

interface ClassPropertyRow extends WebEntityReference {
  staged: boolean;
  stagedId?: string;
}

function RelationshipRows({ title, empty, rows, onDiscard }: { title: string; empty: string; rows: RelationshipRow[]; onDiscard: (entryId: string) => Promise<void> }) {
  return <section className="relationship-row-section">
    <header><h3>{title}</h3><span>{rows.length}</span></header>
    {rows.length ? <ul className="relationship-row-list">
      {rows.map((row) => <li key={row.id} className={row.staged ? "relationship-row-staged" : row.inferredState ? `relationship-row-inferred relationship-row-inferred-${row.inferredState.toLowerCase()}` : undefined}>
        <div><strong>{row.predicate}</strong><span>{row.value}</span></div>
        <div className="relationship-row-status">
          <small>{row.staged ? "Staged" : row.inferredState ? `Inferred · ${row.inferredState}` : "Applied"}</small>
          {row.stagedId ? <button type="button" className="icon-button" aria-label={`Remove staged ${row.predicate} relationship`} onClick={() => void onDiscard(row.stagedId!)}>×</button> : null}
        </div>
      </li>)}
    </ul> : <p className="relationship-row-empty">{empty}</p>}
  </section>;
}

interface ShaclTabProps {
  projectId: string;
  entity: WebEntityDetailResponse;
  stagedFields: ReadonlySet<StagedField>;
  shapesSourceId?: string;
  shapeLabel: string;
  target: SemanticClassChoice[];
  path: SemanticEntityChoice[];
  constraintKind: string;
  constraintValue: string;
  severity: string;
  validationMessage: string;
  pending: boolean;
  onShapeLabelChange: (value: string) => void;
  onTargetChange: (value: SemanticClassChoice[]) => void;
  onPathChange: (value: SemanticEntityChoice[]) => void;
  onConstraintKindChange: (value: string) => void;
  onConstraintValueChange: (value: string) => void;
  onSeverityChange: (value: string) => void;
  onValidationMessageChange: (value: string) => void;
  onStage: (request: Omit<WebStageChangeRequest, "sourceId">, message: string, sourceId?: string) => Promise<boolean>;
  onOpenEntity?: (entity: WebEntityReference, section?: EntitySectionTarget) => void;
}

type ShaclConstraintDialogState =
  | { mode: "add" }
  | {
      mode: "edit";
      shape: WebShaclShapeSummary;
      propertyShape?: WebShaclPropertyShapeSummary;
      constraint: WebShaclConstraintSummary;
      individualReadOnly: boolean;
      targetClass?: WebEntityReference;
    };

function ShaclTab(props: ShaclTabProps) {
  const shapes = useShaclShapes(props.projectId);
  const staged = useStagedChanges(props.projectId);
  const [dialog, setDialog] = useState<ShaclConstraintDialogState | null>(null);
  const kind = props.entity.kind.toLowerCase().replaceAll(" ", "");
  const isClass = kind === "class";
  const isProperty = kind.endsWith("property");
  const isIndividual = !isClass && !isProperty;
  const typeIris = new Set(props.entity.assertedTypes.map((type) => type.iri));
  const contextualShapes = (shapes.data?.shapes ?? []).filter((shape) => {
    if (isClass) return shape.targets.some((target) => target.kind === "TargetClass" && target.iri === props.entity.iri);
    if (isProperty) return shape.propertyShapes.some((propertyShape) => propertyShape.path.iri === props.entity.iri)
      || shape.targets.some((target) => ["TargetSubjectsOf", "TargetObjectsOf"].includes(target.kind) && target.iri === props.entity.iri);
    return shape.targets.some((target) =>
      (target.kind === "TargetNode" && target.iri === props.entity.iri)
      || (target.kind === "TargetClass" && typeIris.has(target.iri)));
  });
  const contextualStaged = (staged.data?.entries ?? []).filter((entry) => {
    if (!entry.editType.startsWith("shacl-")) return false;
    if (isClass) return entry.normalizedValues.targetClassIri === props.entity.iri;
    if (isProperty) return entry.normalizedValues.pathIri === props.entity.iri;
    return entry.normalizedValues.targetIri === props.entity.iri || typeIris.has(entry.normalizedValues.targetClassIri);
  });

  const title = isClass ? "Class constraints" : isProperty ? "Constraint usage" : "Applicable validation rules";
  const description = isClass
    ? `Rules shown here target ${props.entity.label}. New property constraints use this class automatically.`
    : isProperty
      ? `Rules shown here use ${props.entity.label} as a SHACL property path.`
      : "Individuals inherit class-targeted rules through their asserted types. Author and manage shapes from the Constraints workspace.";

  if (shapes.isPending) return <p role="status" className="entity-tab-loading">Loading constraints...</p>;
  if (shapes.isError) return <p role="alert" className="workflow-error">Could not load constraints. {shapes.error.message}</p>;

  const selectedTarget = isClass ? [{ iri: props.entity.iri, label: props.entity.label, kind: "Class", sourceId: props.entity.sourceId, staged: false }] : props.target;
  const selectedPath = isProperty ? [{ iri: props.entity.iri, label: props.entity.label, kind: props.entity.kind, sourceId: props.entity.sourceId, staged: false }] : props.path;
  const canAuthor = (isClass || isProperty) && Boolean(props.shapesSourceId);
  const constraintRows = contextualShapes.flatMap((shape) => {
    const nodeRows = shape.constraints.map((constraint, index) => ({
      id: `${shape.iri}:node:${constraint.kind}:${index}`,
      shape,
      constraint,
      propertyShape: undefined,
      context: "Node shape",
    }));
    const propertyRows = shape.propertyShapes
      .filter((propertyShape) => !isProperty || propertyShape.path.iri === props.entity.iri)
      .flatMap((propertyShape) => propertyShape.constraints.map((constraint, index) => ({
        id: `${shape.iri}:${propertyShape.iri}:${constraint.kind}:${index}`,
        shape,
        constraint,
        propertyShape,
        context: propertyShape.path.label,
      })));
    return [...nodeRows, ...propertyRows];
  });

  return <div className={`contextual-shacl-workspace ${props.stagedFields.has("shacl") ? "staged-field-group" : ""}`}>
    <header className="contextual-shacl-header">
      <div><h3>{title}</h3><p>{description}</p></div>
      <div className="contextual-shacl-actions"><span>{constraintRows.length + contextualStaged.length}</span>{canAuthor ? <button className="button small" type="button" onClick={() => setDialog({ mode: "add" })}>Add constraint</button> : null}</div>
    </header>
    {constraintRows.length || contextualStaged.length ? <div className="contextual-shacl-list">
      {constraintRows.map((row) => <button className="contextual-constraint-row" type="button" key={row.id} onClick={() => setDialog({
        mode: "edit",
        shape: row.shape,
        propertyShape: row.propertyShape,
        constraint: row.constraint,
        individualReadOnly: isIndividual,
        targetClass: isIndividual ? targetClassReference(row.shape, typeIris, props.entity.sourceId) : undefined,
      })}>
        <div><strong>{row.shape.label}</strong><span>{row.context}</span></div>
        <div className="contextual-constraint-summary"><strong>{formatConstraintKind(row.constraint.kind)}</strong><span>{row.constraint.valueLabel ?? row.constraint.value ?? "Enabled"}</span></div>
        <StatusBadge tone={row.shape.severity === "Violation" ? "danger" : "neutral"}>{row.shape.severity}</StatusBadge>
      </button>)}
      {contextualStaged.map((entry) => <article className="shacl-shape-card shacl-shape-card-staged" key={entry.id}>
        <div><strong>{entry.normalizedValues.shapeLabel || entry.summary}</strong><span>Staged for proposal review</span></div>
        <StatusBadge tone="staged">Staged</StatusBadge>
      </article>)}
    </div> : <div className="entity-tab-empty"><strong>No applicable constraints</strong><span>{isProperty ? "This property is not used by an applied SHACL shape." : "No applied SHACL shape targets this entity."}</span></div>}
    {dialog ? <ShaclConstraintDialog
      state={dialog}
      projectId={props.projectId}
      entity={props.entity}
      shapesSourceId={props.shapesSourceId}
      selectedTarget={selectedTarget}
      selectedPath={selectedPath}
      shapeLabel={props.shapeLabel}
      constraintKind={props.constraintKind}
      constraintValue={props.constraintValue}
      severity={props.severity}
      validationMessage={props.validationMessage}
      pending={props.pending}
      onShapeLabelChange={props.onShapeLabelChange}
      onTargetChange={props.onTargetChange}
      onPathChange={props.onPathChange}
      onConstraintKindChange={props.onConstraintKindChange}
      onConstraintValueChange={props.onConstraintValueChange}
      onSeverityChange={props.onSeverityChange}
      onValidationMessageChange={props.onValidationMessageChange}
      onStage={props.onStage}
      onOpenEntity={props.onOpenEntity}
      onClose={() => setDialog(null)}
    /> : null}
  </div>;
}

interface ShaclConstraintDialogProps extends Omit<ShaclTabProps, "target" | "path" | "stagedFields"> {
  state: ShaclConstraintDialogState;
  selectedTarget: SemanticClassChoice[];
  selectedPath: SemanticEntityChoice[];
  onClose: () => void;
}

function ShaclConstraintDialog(props: ShaclConstraintDialogProps) {
  const editing = props.state.mode === "edit" ? props.state : null;
  const [shapeName, setShapeName] = useState(editing?.shape.label ?? props.shapeLabel);
  const [value, setValue] = useState(editing?.constraint.valueLabel ?? editing?.constraint.value ?? props.constraintValue);
  const individualReadOnly = Boolean(editing?.individualReadOnly);
  const editable = Boolean(editing?.propertyShape) && !individualReadOnly;
  const originalValue = editing?.constraint.valueLabel ?? editing?.constraint.value ?? "";
  const shapeNameChanged = Boolean(editing && shapeName.trim() !== editing.shape.label);
  const constraintValueChanged = Boolean(editing && editable && value.trim() !== originalValue);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape") props.onClose(); };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [props.onClose]);

  async function addConstraint(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const target = props.selectedTarget[0];
    const path = props.selectedPath[0];
    if (!target || !path || !props.shapesSourceId) return;
    const staged = await props.onStage({
      editType: "shacl-create-property-shape",
      shapeLabel: props.shapeLabel.trim(),
      targetClassIri: target.iri,
      targetClassLabel: target.label,
      pathIri: path.iri,
      pathLabel: path.label,
      constraintKind: props.constraintKind,
      constraintValue: props.constraintValue.trim(),
      severity: props.severity,
      validationMessage: props.validationMessage.trim() || undefined,
    }, "SHACL constraint staged for proposal review.", props.shapesSourceId);
    if (staged) props.onClose();
  }

  async function updateConstraint(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || !shapeName.trim() || (!shapeNameChanged && !constraintValueChanged)) return;

    if (shapeNameChanged) {
      const renamed = await props.onStage({
        editType: "shacl-update-shape-label",
        shapeIri: editing.shape.iri,
        shapeLabel: editing.shape.label,
        label: shapeName.trim(),
      }, "SHACL shape name update staged for proposal review.", editing.shape.sourceId);
      if (!renamed) return;
    }

    if (constraintValueChanged && editing.propertyShape) {
      const updated = await props.onStage({
        editType: "shacl-update-constraint",
        shapeIri: editing.shape.iri,
        shapeLabel: shapeName.trim(),
        pathIri: editing.propertyShape.path.iri,
        pathLabel: editing.propertyShape.path.label,
        constraintKind: editing.constraint.kind,
        constraintValue: value.trim(),
      }, "SHACL constraint update staged for proposal review.", editing.shape.sourceId);
      if (!updated) return;
    }

    props.onClose();
  }

  async function removeConstraint() {
    if (!editing?.propertyShape || individualReadOnly) return;
    const staged = await props.onStage({
      editType: "shacl-remove-constraint",
      shapeIri: editing.shape.iri,
      shapeLabel: editing.shape.label,
      pathIri: editing.propertyShape.path.iri,
      pathLabel: editing.propertyShape.path.label,
      constraintKind: editing.constraint.kind,
    }, "SHACL constraint removal staged for proposal review.", editing.shape.sourceId);
    if (staged) props.onClose();
  }

  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) props.onClose(); }}>
    <section className="edit-dialog shacl-constraint-dialog" role="dialog" aria-modal="true" aria-label={editing ? shapeName || "Constraint edit" : "Add constraint"}>
      <header className="edit-dialog-header"><div><span className="overline">Constraint edit</span><h2>{editing ? <input className="shacl-shape-name-input" aria-label="Shape name" value={shapeName} disabled={individualReadOnly} onChange={(event) => setShapeName(event.target.value)} /> : "Add constraint"}</h2></div><button className="icon-button" type="button" aria-label="Close constraint dialog" onClick={props.onClose}>×</button></header>
      {editing ? <form onSubmit={updateConstraint}>
        <div className="shacl-dialog-context">
          <ReadOnlyShaclContext label="Target" value={editing.shape.targets.map((target) => target.label).join(", ") || "No target"} />
          <ReadOnlyShaclContext label="Property path" value={editing.propertyShape?.path.label ?? "Node shape"} />
          <ReadOnlyShaclContext label="Constraint" value={formatConstraintKind(editing.constraint.kind)} />
          <ReadOnlyShaclContext label="Severity" value={editing.propertyShape?.severity ?? editing.shape.severity} />
        </div>
        <label>Constraint value<input value={value} onChange={(event) => setValue(event.target.value)} disabled={!editable} /></label>
        {!editable && !individualReadOnly ? <p className="workflow-warning" role="note">This node-level rule can be inspected here, but the current typed edit contract only updates direct property constraints.</p> : null}
        <details className="technical-details"><summary>Technical details</summary><dl><div><dt>Shape IRI</dt><dd><code>{editing.shape.iri}</code></dd></div>{editing.propertyShape ? <div><dt>Property shape</dt><dd><code>{editing.propertyShape.iri}</code></dd></div> : null}</dl></details>
        {individualReadOnly ? <div className="constraint-target-guidance" role="note">
          {editing.targetClass && props.onOpenEntity ? <>To edit or remove this constraint, see <button className="inline-link-button" type="button" onClick={() => { props.onClose(); props.onOpenEntity?.(editing.targetClass!, "shacl"); }}>{editing.targetClass.label}</button>.</> : "This inherited constraint must be managed from its target class."}
        </div> : <div className="dialog-actions"><button className="button" type="button" onClick={props.onClose}>Cancel</button>{editable ? <button className="button danger" type="button" disabled={props.pending} onClick={() => void removeConstraint()}>Remove constraint</button> : null}<button className="button primary" type="submit" disabled={props.pending || !shapeName.trim() || (!shapeNameChanged && !constraintValueChanged)}>Stage update</button></div>}
      </form> : <form onSubmit={addConstraint}>
        <p className="edit-dialog-description">The new rule enters the shared review queue. The shapes graph changes only after proposal approval.</p>
        <label>Shape label<input autoFocus value={props.shapeLabel} onChange={(event) => props.onShapeLabelChange(event.target.value)} /></label>
        {props.entity.kind.toLowerCase() === "class" ? <ReadOnlyShaclContext label="Target class" value={props.entity.label} /> : <SemanticClassPicker projectId={props.projectId} id="shacl-target-class" label="Target class" selected={props.selectedTarget} onChange={props.onTargetChange} multiple={false} />}
        {props.entity.kind.toLowerCase() === "class" ? <SemanticEntityPicker projectId={props.projectId} id="shacl-property-path" label="Property path" selected={props.selectedPath} onChange={props.onPathChange} accepts={acceptsProperty} placeholder="Search properties" help="Choose a direct property path." multiple={false} selectedValueInInput selectionPresentation="hidden" includeStaged={false} /> : <ReadOnlyShaclContext label="Property path" value={props.entity.label} />}
        <div className="shacl-dialog-grid"><label>Constraint<select value={props.constraintKind} onChange={(event) => props.onConstraintKindChange(event.target.value)}>{SHACL_CONSTRAINTS.map((constraint) => <option key={constraint.value} value={constraint.value}>{constraint.label}</option>)}</select></label><label>Constraint value<input value={props.constraintValue} onChange={(event) => props.onConstraintValueChange(event.target.value)} placeholder="1" /></label><label>Severity<select value={props.severity} onChange={(event) => props.onSeverityChange(event.target.value)}><option>Violation</option><option>Warning</option><option>Info</option></select></label></div>
        <label>Validation message<input value={props.validationMessage} onChange={(event) => props.onValidationMessageChange(event.target.value)} placeholder={`Explain the ${props.entity.label} constraint`} /></label>
        <div className="dialog-actions"><button className="button" type="button" onClick={props.onClose}>Cancel</button><button className="button primary" type="submit" disabled={props.pending || !props.shapeLabel.trim() || props.selectedTarget.length === 0 || props.selectedPath.length === 0 || !props.constraintValue.trim()}>Add to review queue</button></div>
      </form>}
    </section>
  </div>;
}

function ReadOnlyShaclContext({ label, value }: { label: string; value: string }) {
  return <div className="shacl-readonly-context"><span>{label}</span><strong>{value}</strong></div>;
}

function targetClassReference(shape: WebShaclShapeSummary, assertedTypeIris: ReadonlySet<string>, sourceId: string): WebEntityReference | undefined {
  const target = shape.targets.find((candidate) => candidate.kind === "TargetClass" && assertedTypeIris.has(candidate.iri));
  return target ? { iri: target.iri, label: target.label, kind: "Class", sourceId } : undefined;
}

function formatConstraintKind(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^./, (character) => character.toUpperCase());
}

function formatShaclTarget(value: string): string {
  return formatConstraintKind(value.replace(/^Target/, "Target "));
}

function EditableFactSection({ title, values, staged, unavailable, children }: { title: string; values: string[]; staged?: boolean; unavailable?: string; children?: React.ReactNode }) {
  return <section className={`editable-fact-section ${staged ? "staged-field" : ""}`} aria-labelledby={`${slug(title)}-heading`}>
    <div className="editable-fact-heading">
      <h3 id={`${slug(title)}-heading`}>{title}</h3>
      <FactValues values={values} />
    </div>
    {unavailable ? <p className="fact-guidance">{unavailable}</p> : children}
  </section>;
}

function SemanticListSection({ title, staged, children }: { title: string; staged?: boolean; children: React.ReactNode }) {
  return <section className={`semantic-list-section ${staged ? "staged-field-group" : ""}`} aria-labelledby={`${slug(title)}-heading`}>
    <h3 id={`${slug(title)}-heading`}>{title}</h3>
    {children}
  </section>;
}

function FactValues({ values }: { values: string[] }) {
  if (!values.length) return <span className="empty-detail-value">N/A</span>;
  return <ul className="fact-value-list">{values.map((value, index) => <li key={`${value}:${index}`}>{value}</li>)}</ul>;
}

function ExternalEntityOverview({ entity }: { entity: WebEntityDetailResponse }) {
  return <div className="entity-tab-sections">
    <EditableFactSection title="Definitions" values={entity.definitions.map((item) => item.value)} />
    <EditableFactSection title="Types" values={entity.assertedTypes.map((item) => item.label)} />
    <EditableFactSection title="Superclasses" values={entity.directSuperclasses.map((item) => item.label)} />
    <details className="technical-details"><summary>Technical details</summary><dl><div><dt>IRI</dt><dd><code>{entity.iri}</code></dd></div></dl></details>
  </div>;
}

function EditorTab<T extends string>({ id, label, active, onSelect }: { id: T; label: string; active: T; onSelect: (id: T) => void }) {
  return <button type="button" role="tab" aria-selected={active === id} className={active === id ? "active" : undefined} onClick={() => onSelect(id)}>{label}</button>;
}

function classChoice(reference: WebEntityReference): SemanticClassChoice {
  return { iri: reference.iri, label: reference.label, kind: "Class", sourceId: reference.sourceId ?? "", staged: false };
}

function entityChoice(entity: WebEntityDetailResponse): SemanticEntityChoice {
  return { iri: entity.iri, label: entity.label, kind: entity.kind, sourceId: entity.sourceId, staged: entity.locality.toLocaleLowerCase() === "staged" };
}

function initialShaclTarget(entity: WebEntityDetailResponse): SemanticClassChoice[] {
  if (entity.kind.toLocaleLowerCase() === "class") return [{ ...entityChoice(entity), kind: "Class" }];
  return entity.assertedTypes[0] ? [classChoice(entity.assertedTypes[0])] : [];
}

function initialShaclPath(entity: WebEntityDetailResponse): SemanticEntityChoice[] {
  return entity.kind.toLocaleLowerCase().replaceAll(" ", "").endsWith("property") ? [entityChoice(entity)] : [];
}

function acceptsProperty(kind: string) {
  return kind.toLocaleLowerCase().replaceAll(" ", "").endsWith("property");
}

function acceptsObjectProperty(kind: string) {
  const normalized = kind.toLocaleLowerCase().replaceAll(" ", "");
  return normalized === "objectproperty" || normalized === "property";
}

function acceptsDatatypeProperty(kind: string) {
  return kind.toLocaleLowerCase().replaceAll(" ", "") === "datatypeproperty";
}

function acceptsIndividual(kind: string) {
  const normalized = kind.toLocaleLowerCase().replaceAll(" ", "");
  return normalized === "individual" || normalized === "object";
}

function requestHierarchyKey(request: Omit<WebStageChangeRequest, "sourceId">) {
  return [request.editType, request.classIri, request.superclassIri].join("|");
}

function requestPropertyRelationKey(request: Omit<WebStageChangeRequest, "sourceId">) {
  return [request.editType, request.propertyIri, request.domainClassIri ?? request.rangeIri ?? request.rangeLabel].join("|");
}

function requestTypeKey(request: Omit<WebStageChangeRequest, "sourceId">) {
  return [request.editType, request.resourceIri, request.typeIri].join("|");
}

function stagedEntryKey(entry: WebStagedEntry) {
  if (entry.editType === "assign-type") {
    return [entry.editType, entry.normalizedValues.resourceIri, entry.normalizedValues.typeIri].join("|");
  }
  return [
    entry.editType,
    entry.normalizedValues.classIri ?? entry.normalizedValues.propertyIri,
    entry.normalizedValues.superclassIri
      ?? entry.normalizedValues.domainClassIri
      ?? entry.normalizedValues.rangeIri
      ?? entry.normalizedValues.rangeLabel,
  ].join("|");
}

function stagedChoice(entry: WebStagedEntry, iriKey: string, labelKey: string, kind: string): SemanticEntityChoice {
  return {
    iri: entry.normalizedValues[iriKey],
    label: entry.normalizedValues[labelKey],
    kind,
    sourceId: entry.sourceId,
    staged: true,
  };
}

function mergeChoices(applied: SemanticEntityChoice[], staged: SemanticEntityChoice[]) {
  const choices = new Map(applied.map((choice) => [choice.iri, choice]));
  staged.forEach((choice) => choices.set(choice.iri, choice));
  return [...choices.values()];
}

function readableDatatype(reference: WebEntityReference) {
  return reference.iri.split(/[#/]/).filter(Boolean).at(-1) ?? reference.label;
}

/**
 * Overlay pending typed edits on the applied entity response. Source files are
 * intentionally unchanged until approval, so the editor must render the
 * review queue as the current working state while a proposal is pending.
 */
function mergeStagedEntity(entity: WebEntityDetailResponse, entries: WebStagedEntry[]): WebEntityDetailResponse {
  if (!entries.length) return entity;
  const relevantEntries = entries.filter((entry) => entryTouchesEntity(entry, entity.iri));
  if (!relevantEntries.length) return entity;
  const merged: WebEntityDetailResponse = {
    ...entity,
    alternateLabels: [...entity.alternateLabels],
    definitions: [...entity.definitions],
    annotations: [...entity.annotations],
    directSuperclasses: [...entity.directSuperclasses],
    directSubclasses: [...entity.directSubclasses],
    directlyTypedIndividuals: [...entity.directlyTypedIndividuals],
    assertedTypes: [...entity.assertedTypes],
    domains: [...entity.domains],
    ranges: [...entity.ranges],
    outgoingRelationships: [...entity.outgoingRelationships],
    incomingRelationships: [...entity.incomingRelationships],
  };

  relevantEntries.slice().sort((left, right) => left.order - right.order).forEach((entry) => {
    const values = entry.normalizedValues;
    const editType = entry.editType || entry.summary.split(" · ")[0];
    const targetIri = values.targetIri ?? values.resourceIri ?? values.classIri ?? values.propertyIri;
    if (targetIri !== entity.iri && !entryTouchesEntity(entry, entity.iri)) return;

    if (editType === "set-entity-label" && values.resourceIri === entity.iri && values.label) {
      merged.label = values.label;
      merged.preferredLabelSource = "Staged edit";
    }
    if (["add-definition", "replace-definition", "remove-definition"].includes(editType) && values.targetIri === entity.iri) {
      merged.definitions = applyTextEdit(merged.definitions, editType, values.existingValue ?? values.value, values.value);
    }
    if (["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(editType) && values.targetIri === entity.iri) {
      merged.alternateLabels = applyTextEdit(merged.alternateLabels, editType, values.existingValue ?? values.value, values.value);
    }
    if (editType === "add-superclass" && values.classIri === entity.iri && values.superclassIri) {
      merged.directSuperclasses = addReference(merged.directSuperclasses, stagedReference(values.superclassIri, values.superclassLabel, "Class", entry.sourceId));
    }
    if (editType === "remove-superclass" && values.classIri === entity.iri && values.superclassIri) {
      merged.directSuperclasses = merged.directSuperclasses.filter((item) => item.iri !== values.superclassIri);
    }
    if (editType === "add-superclass" && values.superclassIri === entity.iri && values.classIri) {
      merged.directSubclasses = addReference(merged.directSubclasses, stagedReference(values.classIri, values.classLabel, "Class", entry.sourceId));
    }
    if (editType === "remove-superclass" && values.superclassIri === entity.iri && values.classIri) {
      merged.directSubclasses = merged.directSubclasses.filter((item) => item.iri !== values.classIri);
    }
    if (editType === "assign-type" && values.resourceIri === entity.iri && values.typeIri) {
      merged.assertedTypes = addReference(merged.assertedTypes, stagedReference(values.typeIri, values.typeLabel, "Class", entry.sourceId));
    }
    if (["set-property-domain", "remove-property-domain"].includes(editType) && values.propertyIri === entity.iri) {
      const reference = values.domainClassIri ? stagedReference(values.domainClassIri, values.domainClassLabel, "Class", entry.sourceId) : undefined;
      if (editType === "set-property-domain" && reference) merged.domains = addReference(merged.domains, reference);
      if (editType === "remove-property-domain" && values.domainClassIri) merged.domains = merged.domains.filter((item) => item.iri !== values.domainClassIri);
    }
    if (["set-property-range", "remove-property-range"].includes(editType) && values.propertyIri === entity.iri) {
      const reference = values.rangeIri ? stagedReference(values.rangeIri, values.rangeLabel, null, entry.sourceId) : values.rangeLabel ? stagedReference(values.rangeLabel, values.rangeLabel, null, entry.sourceId) : undefined;
      if (editType === "set-property-range" && reference) merged.ranges = addReference(merged.ranges, reference);
      if (editType === "remove-property-range" && values.rangeIri) merged.ranges = merged.ranges.filter((item) => item.iri !== values.rangeIri);
    }
  });
  return merged;
}

function applyTextEdit(values: WebTextValue[], editType: string, existingValue?: string, nextValue?: string): WebTextValue[] {
  const existing = existingValue ?? "";
  if (editType === "remove-definition" || editType === "remove-alternate-label") {
    return values.filter((item) => item.value !== existing);
  }
  if (editType === "replace-definition" || editType === "replace-alternate-label") {
    const replaced = values.map((item) => item.value === existing ? { ...item, value: nextValue ?? "" } : item);
    return nextValue && !replaced.some((item) => item.value === nextValue) ? [...replaced, { value: nextValue, language: null, datatype: null }] : replaced;
  }
  return nextValue && !values.some((item) => item.value === nextValue)
    ? [...values, { value: nextValue, language: null, datatype: null }]
    : values;
}

function addReference(values: WebEntityReference[], reference: WebEntityReference): WebEntityReference[] {
  return values.some((item) => item.iri === reference.iri) ? values : [...values, reference];
}

function stagedReference(iri: string, label: string | undefined, kind: string | null, sourceId: string): WebEntityReference {
  return { iri, label: label ?? readableIriLabel(iri), kind, sourceId };
}

function entryTouchesEntity(entry: WebStagedEntry, iri: string): boolean {
  return Object.values(entry.normalizedValues).includes(iri) || entry.generatedIris.includes(iri);
}

function stagedEntityFields(iri: string, entries: WebStagedEntry[]): ReadonlySet<StagedField> {
  const fields = new Set<StagedField>();
  entries.forEach((entry) => {
    const values = entry.normalizedValues;
    const type = entry.editType || entry.summary.split(" · ")[0];
    if (["create-class", "create-individual", "create-object-property", "create-datatype-property"].includes(type) && entry.generatedIris.includes(iri)) fields.add("preferredLabel");
    if (type === "set-entity-label" && values.resourceIri === iri) fields.add("preferredLabel");
    if (["add-definition", "replace-definition", "remove-definition"].includes(type) && values.targetIri === iri) fields.add("definition");
    if (["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(type) && values.targetIri === iri) fields.add("alternateLabel");
    if (type === "assign-type" && values.resourceIri === iri) fields.add("types");
    if (["add-superclass", "remove-superclass"].includes(type)) {
      if (values.classIri === iri) fields.add("superclasses");
      if (values.superclassIri === iri) fields.add("subclasses");
    }
    if (["set-property-domain", "remove-property-domain"].includes(type)) {
      if (values.propertyIri === iri) fields.add("domains");
      if (values.domainClassIri === iri) fields.add("properties");
    }
    if (["set-property-range", "remove-property-range"].includes(type)) {
      if (values.propertyIri === iri) fields.add("ranges");
      if (values.rangeIri === iri) fields.add("properties");
    }
    if (["add-object-property-assertion", "add-datatype-property-assertion"].includes(type)
      && (values.subjectIri === iri || values.objectIri === iri)) fields.add("relationships");
    if (type.startsWith("shacl-") && entryTouchesEntity(entry, iri)) fields.add("shacl");
  });
  return fields;
}

function relationshipRows(entity: WebEntityDetailResponse, entries: WebStagedEntry[], kind: "outgoing" | "incoming" | "datatype"): RelationshipRow[] {
  const appliedRelationships = kind === "incoming" ? entity.incomingRelationships : entity.outgoingRelationships;
  const applied = appliedRelationships
    .filter((relationship) => !STRUCTURAL_PREDICATES.has(relationship.predicate.iri))
    .filter((relationship) => kind === "datatype" ? !isResourceRelationship(relationship) : isResourceRelationship(relationship))
    .map((relationship) => ({
      id: `applied:${kind}:${relationship.predicate.iri}:${relationship.value.value}`,
      predicate: relationship.predicate.label,
      value: kind === "datatype" ? formatLiteralValue(relationship.value) : formatValue(relationship.value),
      staged: false,
    }));
  const staged = entries.flatMap((entry): RelationshipRow[] => {
    if (kind === "outgoing" && entry.editType === "add-object-property-assertion" && entry.normalizedValues.subjectIri === entity.iri) {
      return [stagedRelationshipRow(entry, entry.normalizedValues.propertyLabel, entry.normalizedValues.objectLabel)];
    }
    if (kind === "incoming" && entry.editType === "add-object-property-assertion" && entry.normalizedValues.objectIri === entity.iri) {
      return [stagedRelationshipRow(entry, entry.normalizedValues.propertyLabel, entry.normalizedValues.subjectLabel)];
    }
    if (kind === "datatype" && entry.editType === "add-datatype-property-assertion" && entry.normalizedValues.subjectIri === entity.iri) {
      return [stagedRelationshipRow(
        entry,
        entry.normalizedValues.propertyLabel,
        formatStagedLiteral(entry.normalizedValues.value, entry.normalizedValues.datatypeIri),
      )];
    }
    return [];
  });
  const inferred = (entity.inferredOverlays ?? []).flatMap((overlay): RelationshipRow[] =>
    overlay.facts.flatMap((fact): RelationshipRow[] => {
      const outgoingType = kind === "outgoing" && fact.kind === "IndividualType" && fact.subject === entity.iri;
      const outgoingAssertion = kind === "outgoing" && fact.kind === "ObjectPropertyAssertion" && fact.subject === entity.iri;
      const incomingAssertion = kind === "incoming" && fact.kind === "ObjectPropertyAssertion" && fact.objectValue === entity.iri;
      if (!outgoingType && !outgoingAssertion && !incomingAssertion) return [];
      return [{
        id: `inferred:${overlay.graphState}:${fact.semanticFactKey}`,
        predicate: fact.kind === "IndividualType" ? "type" : technicalLabel(fact.predicate),
        value: technicalLabel(outgoingType || outgoingAssertion ? fact.objectValue : fact.subject),
        staged: false,
        inferredState: overlay.graphState,
      }];
    }),
  );
  return [...staged, ...applied, ...inferred];
}

function stagedRelationshipRow(entry: WebStagedEntry, predicate?: string, value?: string): RelationshipRow {
  return {
    id: `staged:${entry.id}`,
    predicate: predicate || "Relationship",
    value: value || "Pending value",
    staged: true,
    stagedId: entry.id,
  };
}

function isResourceRelationship(relationship: WebRelationship) {
  const kind = relationship.value.kind.toLocaleLowerCase().replaceAll(" ", "");
  return kind === "iri" || kind === "blanknode" || kind === "resource";
}

function classProperties(entity: WebEntityDetailResponse, predicateIri: string): WebEntityReference[] {
  const byIri = new Map<string, WebEntityReference>();
  entity.incomingRelationships
    .filter((relationship) => relationship.predicate.iri === predicateIri && relationship.value.kind.toLocaleLowerCase() === "iri")
    .forEach((relationship) => byIri.set(relationship.value.value, {
      iri: relationship.value.value,
      label: formatValue(relationship.value),
      kind: relationship.value.entityKind ?? null,
      sourceId: relationship.sourceId,
    }));
  return [...byIri.values()].sort((left, right) => left.label.localeCompare(right.label));
}

function classPropertyRows(
  entity: WebEntityDetailResponse,
  entries: WebStagedEntry[],
  direction: ClassPropertyDirection,
): ClassPropertyRow[] {
  const predicate = direction === "incoming" ? RDFS_RANGE : RDFS_DOMAIN;
  const rows = new Map<string, ClassPropertyRow>(
    classProperties(entity, predicate).map((property) => [property.iri, { ...property, staged: false }]),
  );
  const stagedKinds = stagedPropertyKinds(entries);

  entries.forEach((entry) => {
    const values = entry.normalizedValues;
    const relevant = direction === "incoming"
      ? ["set-property-range", "remove-property-range"].includes(entry.editType) && values.rangeIri === entity.iri
      : ["set-property-domain", "remove-property-domain"].includes(entry.editType) && values.domainClassIri === entity.iri;
    if (!relevant || !values.propertyIri) return;
    const existing = rows.get(values.propertyIri);
    rows.set(values.propertyIri, {
      iri: values.propertyIri,
      label: values.propertyLabel ?? existing?.label ?? readableIriLabel(values.propertyIri),
      kind: existing?.kind ?? stagedKinds.get(values.propertyIri) ?? "ObjectProperty",
      sourceId: entry.sourceId,
      staged: true,
      stagedId: entry.id,
    });
  });

  return [...rows.values()]
    .filter((property) => direction === "datatype"
      ? property.kind?.toLocaleLowerCase() === "datatypeproperty"
      : property.kind?.toLocaleLowerCase() !== "datatypeproperty")
    .sort((left, right) => left.label.localeCompare(right.label));
}

function stagedPropertyKinds(entries: WebStagedEntry[]): Map<string, string> {
  const kinds = new Map<string, string>();
  entries.forEach((entry) => {
    if (entry.editType === "create-object-property") entry.generatedIris.forEach((iri) => kinds.set(iri, "ObjectProperty"));
    if (entry.editType === "create-datatype-property") entry.generatedIris.forEach((iri) => kinds.set(iri, "DatatypeProperty"));
    if (entry.editType === "set-property-range" && entry.normalizedValues.propertyIri?.startsWith("http") && entry.normalizedValues.rangeIri?.startsWith(XSD_NAMESPACE)) {
      kinds.set(entry.normalizedValues.propertyIri, "DatatypeProperty");
    }
  });
  return kinds;
}

function formatValue(value: WebRdfValue) {
  return value.label ?? KNOWN_RESOURCE_LABELS.get(value.value) ?? readableIriLabel(value.value);
}

function formatLiteralValue(value: WebRdfValue) {
  return formatStagedLiteral(formatValue(value), value.datatype ?? undefined);
}

function formatStagedLiteral(value?: string, datatypeIri?: string) {
  const lexicalValue = value || "Pending value";
  return datatypeIri ? `${lexicalValue} · ${datatypeLabel(datatypeIri)}` : lexicalValue;
}

function datatypeLabel(datatypeIri: string) {
  return datatypeIri.startsWith(XSD_NAMESPACE) ? `xsd:${datatypeIri.slice(XSD_NAMESPACE.length)}` : readableIriLabel(datatypeIri);
}

function readableIriLabel(value: string) {
  if (!/^https?:\/\//.test(value)) return value;
  const localName = value.split(/[#/]/).filter(Boolean).at(-1) ?? value;
  return decodeURIComponent(localName).replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[-_]/g, " ");
}

function slug(value: string) {
  return value.toLocaleLowerCase().replaceAll(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

const STANDARD_DATATYPES = ["string", "boolean", "integer", "decimal", "date", "dateTime"];

const XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema#";
const XSD_STRING = `${XSD_NAMESPACE}string`;
const LITERAL_DATATYPES = [
  { label: "xsd:string", iri: XSD_STRING },
  { label: "xsd:integer", iri: `${XSD_NAMESPACE}integer` },
  { label: "xsd:decimal", iri: `${XSD_NAMESPACE}decimal` },
  { label: "xsd:float", iri: `${XSD_NAMESPACE}float` },
  { label: "xsd:double", iri: `${XSD_NAMESPACE}double` },
  { label: "xsd:boolean", iri: `${XSD_NAMESPACE}boolean` },
  { label: "xsd:date", iri: `${XSD_NAMESPACE}date` },
  { label: "xsd:dateTime", iri: `${XSD_NAMESPACE}dateTime` },
  { label: "xsd:anyURI", iri: `${XSD_NAMESPACE}anyURI` },
] as const;

function datatypeInputType(datatypeIri: string): "text" | "number" | "date" | "datetime-local" {
  if (datatypeIri === `${XSD_NAMESPACE}date`) return "date";
  if (datatypeIri === `${XSD_NAMESPACE}dateTime`) return "datetime-local";
  if (isNumericDatatype(datatypeIri)) return "number";
  return "text";
}

function isNumericDatatype(datatypeIri: string): boolean {
  return ["integer", "decimal", "float", "double"].some((name) => datatypeIri === `${XSD_NAMESPACE}${name}`);
}

function datatypePlaceholder(datatypeIri: string): string {
  if (datatypeIri === `${XSD_NAMESPACE}boolean`) return "true or false";
  if (datatypeIri === `${XSD_NAMESPACE}anyURI`) return "https://example.com/resource";
  return isNumericDatatype(datatypeIri) ? "Enter a numeric value" : "Enter a value";
}
const RDFS_DOMAIN = "http://www.w3.org/2000/01/rdf-schema#domain";
const RDFS_RANGE = "http://www.w3.org/2000/01/rdf-schema#range";
const CLASS_PROPERTY_SECTIONS: Record<ClassPropertyDirection, {
  title: string;
  description: (classLabel: string) => string;
  empty: string;
}> = {
  outgoing: {
    title: "Outgoing properties",
    description: (classLabel) => `Object properties whose domain is ${classLabel}.`,
    empty: "No object properties use this class as their domain.",
  },
  incoming: {
    title: "Incoming properties",
    description: (classLabel) => `Object properties whose range is ${classLabel}.`,
    empty: "No object properties use this class as their range.",
  },
  datatype: {
    title: "Datatype properties",
    description: (classLabel) => `Datatype properties whose domain is ${classLabel}.`,
    empty: "No datatype properties use this class as their domain.",
  },
};
const STRUCTURAL_PREDICATES = new Set([
  "http://www.w3.org/2000/01/rdf-schema#subClassOf",
  "http://www.w3.org/2000/01/rdf-schema#domain",
  "http://www.w3.org/2000/01/rdf-schema#range",
]);
const KNOWN_RESOURCE_LABELS = new Map([
  ["http://www.w3.org/2000/01/rdf-schema#Class", "Class"],
  ["http://www.w3.org/1999/02/22-rdf-syntax-ns#Property", "Property"],
  ["http://www.w3.org/2002/07/owl#Class", "Class"],
  ["http://www.w3.org/2002/07/owl#ObjectProperty", "Object property"],
  ["http://www.w3.org/2002/07/owl#DatatypeProperty", "Datatype property"],
  ["http://www.w3.org/2002/07/owl#NamedIndividual", "Individual"],
]);
const SHACL_CONSTRAINTS = [
  { value: "min-count", label: "Minimum count" },
  { value: "max-count", label: "Maximum count" },
  { value: "datatype", label: "Datatype" },
  { value: "class", label: "Class" },
  { value: "min-inclusive", label: "Minimum inclusive" },
  { value: "max-inclusive", label: "Maximum inclusive" },
  { value: "pattern", label: "Pattern" },
];
