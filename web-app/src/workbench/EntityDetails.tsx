import { useEffect, useRef, useState } from "react";
import { useEntityDetails, useProjectSources, useStagedChanges, useStagingActions } from "../web/queries";
import type {
  WebEntityDetailResponse,
  WebEntityReference,
  WebRdfValue,
  WebRelationship,
  WebStageChangeRequest,
  WebStagedEntry,
} from "../web/projectApi";
import StatusBadge from "../components/ui/StatusBadge";
import SemanticClassPicker, { type SemanticClassChoice } from "./SemanticClassPicker";
import SemanticEntityPicker, { type SemanticEntityChoice } from "./SemanticEntityPicker";

interface EntityDetailsProps {
  projectId: string;
  iri: string;
  stagedEntity?: WebEntityDetailResponse;
}

type EditorSectionId = "overview" | "hierarchy" | "properties" | "schema" | "relationships" | "shacl";
type ClassPropertyDirection = "outgoing" | "incoming";

export default function EntityDetails({ projectId, iri, stagedEntity }: EntityDetailsProps) {
  const details = useEntityDetails(projectId, iri, !stagedEntity);

  if (!stagedEntity && details.isPending) return <p role="status">Loading entity details...</p>;
  if (!stagedEntity && details.isError) return <p role="alert">Could not load this entity. {details.error.message}</p>;
  const entity = stagedEntity ?? details.data;
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
      {entity.locality !== "External"
        ? <EntityDetailWorkspace projectId={projectId} entity={entity} />
        : <ExternalEntityOverview entity={entity} />}
    </article>
  );
}

function EntityDetailWorkspace({ projectId, entity }: { projectId: string; entity: WebEntityDetailResponse }) {
  const actions = useStagingActions(projectId);
  const stagedChanges = useStagedChanges(projectId);
  const sources = useProjectSources(projectId);
  const [activeSection, setActiveSection] = useState<EditorSectionId>("overview");
  const [preferredLabel, setPreferredLabel] = useState(entity.label);
  const [definition, setDefinition] = useState(entity.definitions[0]?.value ?? "");
  const [alternateLabel, setAlternateLabel] = useState(entity.alternateLabels[0]?.value ?? "");
  const [superclasses, setSuperclasses] = useState<SemanticClassChoice[]>(() => entity.directSuperclasses.map(classChoice));
  const [subclass, setSubclass] = useState<SemanticClassChoice[]>([]);
  const [type, setType] = useState<SemanticClassChoice[]>([]);
  const [domain, setDomain] = useState<SemanticClassChoice[]>(() => entity.domains[0] ? [classChoice(entity.domains[0])] : []);
  const [range, setRange] = useState<SemanticClassChoice[]>(() => entity.ranges[0] ? [classChoice(entity.ranges[0])] : []);
  const [datatypeRange, setDatatypeRange] = useState(entity.ranges[0]?.label ?? "string");
  const [outgoingProperty, setOutgoingProperty] = useState<SemanticEntityChoice[]>([]);
  const [outgoingObject, setOutgoingObject] = useState<SemanticEntityChoice[]>([]);
  const [datatypeProperty, setDatatypeProperty] = useState<SemanticEntityChoice[]>([]);
  const [literalValue, setLiteralValue] = useState("");
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
    stagedEntries.current = stagedChanges.data?.entries ?? [];
  }, [stagedChanges.data?.entries]);

  useEffect(() => () => {
    autoStageTimers.current.forEach((timer) => window.clearTimeout(timer));
    autoStageTimers.current.clear();
  }, []);

  useEffect(() => {
    setActiveSection("overview");
    setPreferredLabel(entity.label);
    setDefinition(entity.definitions[0]?.value ?? "");
    setAlternateLabel(entity.alternateLabels[0]?.value ?? "");
    setSuperclasses(entity.directSuperclasses.map(classChoice));
    setSubclass([]);
    setType(entity.assertedTypes.map(classChoice));
    setDomain(entity.domains[0] ? [classChoice(entity.domains[0])] : []);
    setRange(entity.ranges[0] ? [classChoice(entity.ranges[0])] : []);
    setDatatypeRange(entity.ranges[0]?.label ?? "string");
    setOutgoingProperty([]);
    setOutgoingObject([]);
    setDatatypeProperty([]);
    setLiteralValue("");
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
      label && label !== entity.label
        ? { editType: "set-entity-label", resourceIri: entity.iri, resourceLabel: entity.label, label }
        : null,
      (entry) => entry.editType === "set-entity-label" && entry.normalizedValues.resourceIri === entity.iri,
      "Preferred label synchronized with the review queue.",
    );
  }

  function changeDefinition(value: string) {
    setDefinition(value);
    const original = entity.definitions[0]?.value ?? "";
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
    const original = entity.alternateLabels[0]?.value ?? "";
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
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The detail change could not be staged.");
    }
  }

  async function stageClassProperty(label: string, domainClass: SemanticClassChoice, rangeClass: SemanticClassChoice) {
    setMessage(null);
    setError(null);
    const batchKey = `web-class-property-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    try {
      const created = await actions.stage.mutateAsync({
        sourceId: entity.sourceId,
        editType: "create-object-property",
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
        rangeIri: rangeClass.iri,
        rangeLabel: rangeClass.label,
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
    const applied = entity.domains[0];
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
    const applied = entity.ranges[0];
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
    const applied = entity.ranges[0];
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

  function synchronizeOutgoing(nextProperty: SemanticEntityChoice[], nextObject: SemanticEntityChoice[]) {
    const property = nextProperty[0];
    const object = nextObject[0];
    scheduleAutoChange(
      "outgoing-relationship",
      property && object ? { editType: "add-object-property-assertion", subjectIri: entity.iri, subjectLabel: entity.label, propertyIri: property.iri, propertyLabel: property.label, objectIri: object.iri, objectLabel: object.label } : null,
      (entry) => entry.editType === "add-object-property-assertion" && entry.normalizedValues.subjectIri === entity.iri,
      "Outgoing relationship synchronized with the review queue.",
      0,
    );
  }

  function synchronizeDatatypeValue(nextProperty: SemanticEntityChoice[], nextValue: string) {
    const property = nextProperty[0];
    const value = nextValue.trim();
    scheduleAutoChange(
      "datatype-value",
      property && value ? { editType: "add-datatype-property-assertion", subjectIri: entity.iri, subjectLabel: entity.label, propertyIri: property.iri, propertyLabel: property.label, value } : null,
      (entry) => entry.editType === "add-datatype-property-assertion" && entry.normalizedValues.subjectIri === entity.iri,
      "Datatype value synchronized with the review queue.",
    );
  }

  function synchronizeIncoming(nextSubject: SemanticEntityChoice[], nextProperty: SemanticEntityChoice[]) {
    const subject = nextSubject[0];
    const property = nextProperty[0];
    scheduleAutoChange(
      "incoming-relationship",
      subject && property ? { editType: "add-object-property-assertion", subjectIri: subject.iri, subjectLabel: subject.label, propertyIri: property.iri, propertyLabel: property.label, objectIri: entity.iri, objectLabel: entity.label } : null,
      (entry) => entry.editType === "add-object-property-assertion" && entry.normalizedValues.objectIri === entity.iri,
      "Incoming relationship synchronized with the review queue.",
      0,
    );
  }

  return <section className="entity-tab-workspace" aria-label={`${entity.label} details and editing`}>
    <div className="entity-editor-tabs" role="tablist" aria-label="Entity detail sections">
      <EditorTab id="overview" label="Overview" active={activeSection} onSelect={setActiveSection} />
      <EditorTab id="hierarchy" label="Hierarchy" active={activeSection} onSelect={setActiveSection} />
      {isClass ? <EditorTab id="properties" label="Properties" active={activeSection} onSelect={setActiveSection} /> : null}
      {isProperty ? <EditorTab id="schema" label="Schema" active={activeSection} onSelect={setActiveSection} /> : null}
      {isObject ? <EditorTab id="relationships" label="Relationships" active={activeSection} onSelect={setActiveSection} /> : null}
      <EditorTab id="shacl" label="SHACL" active={activeSection} onSelect={setActiveSection} />
    </div>

    <div className="entity-tab-panel" role="tabpanel">
      {activeSection === "overview" ? <OverviewTab
        entity={entity}
        preferredLabel={preferredLabel}
        definition={definition}
        alternateLabel={alternateLabel}
        onPreferredLabelChange={changePreferredLabel}
        onDefinitionChange={changeDefinition}
        onAlternateLabelChange={changeAlternateLabel}
      /> : null}

      {activeSection === "hierarchy" ? <div className="entity-tab-sections">
        {isObject ? <EditableFactSection title="Types" values={entity.assertedTypes.map((item) => item.label)}>
          <div className="inline-semantic-editor auto-staged-editor">
            <SemanticClassPicker projectId={projectId} id="entity-type" label="Add asserted type" selected={type} onChange={(next) => {
              setType(next);
              const selected = next[0];
              scheduleAutoChange(
                "asserted-type",
                selected ? { editType: "assign-type", resourceIri: entity.iri, resourceLabel: entity.label, typeIri: selected.iri, typeLabel: selected.label } : null,
                (entry) => entry.editType === "assign-type" && entry.normalizedValues.resourceIri === entity.iri,
                "Type synchronized with the review queue.",
                0,
              );
            }} multiple={false} excludeIri={entity.iri} />
          </div>
        </EditableFactSection> : null}

        {isClass ? <EditableFactSection title="Superclasses" values={entity.directSuperclasses.map((item) => item.label)}>
          <div className="inline-semantic-editor auto-staged-editor">
            <SemanticClassPicker projectId={projectId} id="entity-superclasses" label="Edit direct superclasses" selected={superclasses} onChange={(next) => {
              setSuperclasses(next);
              const original = new Map(entity.directSuperclasses.map((item) => [item.iri, item]));
              const current = new Map(next.map((item) => [item.iri, item]));
              const requests: Array<Omit<WebStageChangeRequest, "sourceId">> = [];
              next.filter((item) => !original.has(item.iri)).forEach((item) => requests.push({ editType: "add-superclass", classIri: entity.iri, classLabel: entity.label, superclassIri: item.iri, superclassLabel: item.label }));
              entity.directSuperclasses.filter((item) => !current.has(item.iri)).forEach((item) => requests.push({ editType: "remove-superclass", classIri: entity.iri, classLabel: entity.label, superclassIri: item.iri, superclassLabel: item.label }));
              void synchronizeOperations(
                "superclasses",
                requests,
                (entry) => ["add-superclass", "remove-superclass"].includes(entry.editType) && entry.normalizedValues.classIri === entity.iri,
                requestHierarchyKey,
              );
            }} excludeIri={entity.iri} />
          </div>
        </EditableFactSection> : null}

        {isClass ? <EditableFactSection title="Subclasses" values={entity.directSubclasses.map((item) => item.label)}>
          <div className="inline-semantic-editor auto-staged-editor">
            <SemanticClassPicker projectId={projectId} id="entity-subclass" label="Add existing or staged subclass" selected={subclass} onChange={(next) => {
              setSubclass(next);
              const selected = next[0];
              scheduleAutoChange(
                "subclass",
                selected ? { editType: "add-superclass", classIri: selected.iri, classLabel: selected.label, superclassIri: entity.iri, superclassLabel: entity.label } : null,
                (entry) => entry.editType === "add-superclass" && entry.normalizedValues.superclassIri === entity.iri && entry.normalizedValues.classIri !== entity.iri,
                "Subclass synchronized with the review queue.",
                0,
                selected?.sourceId === "staged" || !selected?.sourceId ? entity.sourceId : selected.sourceId,
              );
            }} multiple={false} excludeIri={entity.iri} />
          </div>
        </EditableFactSection> : null}

        {isProperty ? <div className="entity-tab-empty"><strong>Properties do not have ontology types or superclasses.</strong><span>Use Schema to edit this property&apos;s domain and range.</span></div> : null}
      </div> : null}

      {activeSection === "schema" ? <SchemaTab
        projectId={projectId}
        entity={entity}
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
        pending={actions.stage.isPending}
        onAdd={setPropertyDialog}
        onRemove={(property, direction) => void stage(direction === "outgoing" ? {
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
        }, `${property.label} ${direction === "outgoing" ? "domain" : "range"} association staged for removal.`, property.sourceId ?? entity.sourceId)}
      /> : null}

      {activeSection === "relationships" ? <RelationshipsTab
        projectId={projectId}
        entity={entity}
        editable={isObject}
        outgoingProperty={outgoingProperty}
        outgoingObject={outgoingObject}
        datatypeProperty={datatypeProperty}
        literalValue={literalValue}
        incomingSubject={incomingSubject}
        incomingProperty={incomingProperty}
        onOutgoingPropertyChange={(next) => { setOutgoingProperty(next); synchronizeOutgoing(next, outgoingObject); }}
        onOutgoingObjectChange={(next) => { setOutgoingObject(next); synchronizeOutgoing(outgoingProperty, next); }}
        onDatatypePropertyChange={(next) => { setDatatypeProperty(next); synchronizeDatatypeValue(next, literalValue); }}
        onLiteralValueChange={(next) => { setLiteralValue(next); synchronizeDatatypeValue(datatypeProperty, next); }}
        onIncomingSubjectChange={(next) => { setIncomingSubject(next); synchronizeIncoming(next, incomingProperty); }}
        onIncomingPropertyChange={(next) => { setIncomingProperty(next); synchronizeIncoming(incomingSubject, next); }}
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
        pending={actions.stage.isPending}
        onShapeLabelChange={setShapeLabel}
        onTargetChange={setShaclTarget}
        onPathChange={setShaclPath}
        onConstraintKindChange={setConstraintKind}
        onConstraintValueChange={setConstraintValue}
        onSeverityChange={setSeverity}
        onValidationMessageChange={setValidationMessage}
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
  preferredLabel: string;
  definition: string;
  alternateLabel: string;
  onPreferredLabelChange: (value: string) => void;
  onDefinitionChange: (value: string) => void;
  onAlternateLabelChange: (value: string) => void;
}

function OverviewTab(props: OverviewTabProps) {
  return <div className="entity-tab-sections">
    <EditableFactSection title="Preferred label" values={[props.entity.label]}>
      <div className="inline-value-editor auto-staged-editor">
        <label className="visually-hidden" htmlFor="entity-preferred-label">Preferred label</label>
        <input id="entity-preferred-label" value={props.preferredLabel} onChange={(event) => props.onPreferredLabelChange(event.target.value)} />
      </div>
    </EditableFactSection>
    <EditableFactSection title="Definitions" values={props.entity.definitions.map((item) => item.value)}>
      <div className="inline-value-editor auto-staged-editor">
        <label className="visually-hidden" htmlFor="entity-definition">Definition</label>
        <textarea id="entity-definition" value={props.definition} onChange={(event) => props.onDefinitionChange(event.target.value)} placeholder="Add a human-readable definition" rows={2} />
      </div>
    </EditableFactSection>
    <EditableFactSection title="Alternate labels" values={props.entity.alternateLabels.map((item) => item.value)}>
      <div className="inline-value-editor auto-staged-editor">
        <label className="visually-hidden" htmlFor="entity-alternate-label">Alternate label</label>
        <input id="entity-alternate-label" value={props.alternateLabel} onChange={(event) => props.onAlternateLabelChange(event.target.value)} placeholder="Add an alternate label" />
      </div>
    </EditableFactSection>
    <EditableFactSection title="Annotations" values={props.entity.annotations.map((item) => `${item.property.label}: ${formatValue(item.value)}`)} />
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

function ClassPropertiesTab({
  entity,
  pending,
  onAdd,
  onRemove,
}: {
  entity: WebEntityDetailResponse;
  pending: boolean;
  onAdd: (direction: ClassPropertyDirection) => void;
  onRemove: (property: WebEntityReference, direction: ClassPropertyDirection) => void;
}) {
  const outgoing = classProperties(entity, RDFS_DOMAIN);
  const incoming = classProperties(entity, RDFS_RANGE);
  return <div className="entity-tab-sections class-properties-tab">
    <ClassPropertySection
      title="Outgoing properties"
      description={`Properties whose domain is ${entity.label}.`}
      empty="No properties use this class as their domain."
      properties={outgoing}
      pending={pending}
      onAdd={() => onAdd("outgoing")}
      onRemove={(property) => onRemove(property, "outgoing")}
    />
    <ClassPropertySection
      title="Incoming properties"
      description={`Properties whose range is ${entity.label}.`}
      empty="No properties use this class as their range."
      properties={incoming}
      pending={pending}
      onAdd={() => onAdd("incoming")}
      onRemove={(property) => onRemove(property, "incoming")}
    />
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
  properties: WebEntityReference[];
  pending: boolean;
  onAdd: () => void;
  onRemove: (property: WebEntityReference) => void;
}) {
  return <section className="class-property-section">
    <header>
      <div><h3>{title}</h3><p>{description}</p></div>
      <button className="button small" type="button" onClick={onAdd}>Add property</button>
    </header>
    {properties.length ? <ul className="class-property-list">
      {properties.map((property) => <li key={property.iri}>
        <div><strong>{property.label}</strong><span>Property</span></div>
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
  onSubmit: (label: string, domainClass: SemanticClassChoice, rangeClass: SemanticClassChoice) => Promise<void>;
}) {
  const currentClass: SemanticClassChoice = { iri: entity.iri, label: entity.label, kind: "Class", sourceId: entity.sourceId, staged: entity.locality.toLocaleLowerCase() === "staged" };
  const [label, setLabel] = useState("");
  const [domain, setDomain] = useState<SemanticClassChoice[]>(direction === "outgoing" ? [currentClass] : []);
  const [range, setRange] = useState<SemanticClassChoice[]>(direction === "incoming" ? [currentClass] : []);

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
        if (propertyLabel && domain[0] && range[0]) void onSubmit(propertyLabel, domain[0], range[0]);
      }}>
        <SemanticClassPicker projectId={projectId} id="class-property-domain" label="Domain class" selected={domain} onChange={setDomain} multiple={false} />
        <label htmlFor="class-property-label">Property name<input id="class-property-label" autoFocus value={label} onChange={(event) => setLabel(event.target.value)} placeholder="owns account" required /></label>
        <SemanticClassPicker projectId={projectId} id="class-property-range" label="Range class" selected={range} onChange={setRange} multiple={false} />
        <div className="dialog-actions">
          <button className="button" type="button" onClick={onClose}>Cancel</button>
          <button className="button primary" type="submit" disabled={pending || !label.trim() || domain.length === 0 || range.length === 0}>{pending ? "Adding…" : "Add property"}</button>
        </div>
      </form>
    </section>
  </div>;
}

interface SchemaTabProps {
  projectId: string;
  entity: WebEntityDetailResponse;
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
    <EditableFactSection title="Domains" values={props.entity.domains.map((item) => item.label)} unavailable={!props.isProperty ? "Domain declarations apply to properties." : undefined}>
      {props.isProperty ? <div className="inline-semantic-editor auto-staged-editor">
        <SemanticClassPicker projectId={props.projectId} id="entity-domain" label="Set domain" selected={props.domain} onChange={props.onDomainChange} multiple={false} />
      </div> : null}
    </EditableFactSection>

    <EditableFactSection title="Ranges" values={props.entity.ranges.map((item) => item.label)} unavailable={!props.isProperty ? "Range declarations apply to properties." : undefined}>
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
  editable: boolean;
  outgoingProperty: SemanticEntityChoice[];
  outgoingObject: SemanticEntityChoice[];
  datatypeProperty: SemanticEntityChoice[];
  literalValue: string;
  incomingSubject: SemanticEntityChoice[];
  incomingProperty: SemanticEntityChoice[];
  onOutgoingPropertyChange: (value: SemanticEntityChoice[]) => void;
  onOutgoingObjectChange: (value: SemanticEntityChoice[]) => void;
  onDatatypePropertyChange: (value: SemanticEntityChoice[]) => void;
  onLiteralValueChange: (value: string) => void;
  onIncomingSubjectChange: (value: SemanticEntityChoice[]) => void;
  onIncomingPropertyChange: (value: SemanticEntityChoice[]) => void;
}

function RelationshipsTab(props: RelationshipsTabProps) {
  const outgoing = relationshipLabels(props.entity.outgoingRelationships.filter(isResourceRelationship));
  const datatype = relationshipLabels(props.entity.outgoingRelationships.filter((relationship) => !isResourceRelationship(relationship)));
  const incoming = relationshipLabels(props.entity.incomingRelationships);
  return <div className="entity-tab-sections">
    <EditableFactSection title="Outgoing object relationships" values={outgoing}>
      {props.editable ? <div className="individual-relationship-editor">
        <SemanticEntityPicker projectId={props.projectId} id="entity-outgoing-property" label="Object property" selected={props.outgoingProperty} onChange={props.onOutgoingPropertyChange} accepts={acceptsObjectProperty} placeholder="Search object properties" help="Choose the relationship predicate." selectedValueInInput />
        <SemanticEntityPicker projectId={props.projectId} id="entity-outgoing-object" label="Object" selected={props.outgoingObject} onChange={props.onOutgoingObjectChange} accepts={acceptsIndividual} placeholder="Search individuals" help="Choose the individual that receives this relationship." excludeIri={props.entity.iri} selectedValueInInput />
      </div> : <p className="fact-guidance">Outgoing relationships connect this individual to another individual.</p>}
    </EditableFactSection>

    <EditableFactSection title="Incoming object relationships" values={incoming}>
      {props.editable ? <div className="individual-relationship-editor">
        <SemanticEntityPicker projectId={props.projectId} id="entity-incoming-subject" label="Subject" selected={props.incomingSubject} onChange={props.onIncomingSubjectChange} accepts={acceptsIndividual} placeholder="Search individuals" help="Choose the individual that points here." excludeIri={props.entity.iri} selectedValueInInput />
        <SemanticEntityPicker projectId={props.projectId} id="entity-incoming-property" label="Object property" selected={props.incomingProperty} onChange={props.onIncomingPropertyChange} accepts={acceptsObjectProperty} placeholder="Search object properties" help="Choose the relationship predicate." selectedValueInInput />
      </div> : <p className="fact-guidance">Incoming object relationships are edited from the subject object.</p>}
    </EditableFactSection>

    <EditableFactSection title="Datatype values" values={datatype}>
      {props.editable ? <div className="individual-relationship-editor">
        <SemanticEntityPicker projectId={props.projectId} id="entity-datatype-property" label="Datatype property" selected={props.datatypeProperty} onChange={props.onDatatypePropertyChange} accepts={acceptsDatatypeProperty} placeholder="Search datatype properties" help="Choose the literal-valued property." selectedValueInInput />
        <label className="relationship-value-field" htmlFor="entity-literal-value">
          <span>Value</span>
          <input id="entity-literal-value" value={props.literalValue} onChange={(event) => props.onLiteralValueChange(event.target.value)} placeholder="Enter a string value" />
          <small>Enter the value stored for this individual.</small>
        </label>
      </div> : <p className="fact-guidance">Datatype values attach literal values to this individual.</p>}
    </EditableFactSection>
  </div>;
}

interface ShaclTabProps {
  projectId: string;
  entity: WebEntityDetailResponse;
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
  onStage: (request: Omit<WebStageChangeRequest, "sourceId">, message: string, sourceId?: string) => Promise<void>;
}

function ShaclTab(props: ShaclTabProps) {
  if (!props.shapesSourceId) return <div className="entity-tab-empty"><strong>No writable SHACL shapes source</strong><span>Add a source with the shapes role before authoring constraints.</span></div>;
  return <div className="entity-tab-sections">
    <EditableFactSection title="SHACL constraints" values={[]}>
      <div className="shacl-authoring-panel">
        <p className="fact-guidance">Create a typed property constraint here. Existing constraints remain available in the Constraints workspace.</p>
        <form className="shacl-inline-editor" onSubmit={(event) => {
        event.preventDefault();
        const target = props.target[0];
        const path = props.path[0];
        if (!target || !path) return;
        void props.onStage({
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
        }}>
          <label>Shape label<input value={props.shapeLabel} onChange={(event) => props.onShapeLabelChange(event.target.value)} /></label>
          <SemanticClassPicker projectId={props.projectId} id="shacl-target-class" label="Target class" selected={props.target} onChange={props.onTargetChange} multiple={false} />
          <SemanticEntityPicker projectId={props.projectId} id="shacl-property-path" label="Property path" selected={props.path} onChange={props.onPathChange} accepts={acceptsProperty} placeholder="Search properties" help="Choose a direct property path." multiple={false} includeStaged={false} />
          <label>Constraint<select value={props.constraintKind} onChange={(event) => props.onConstraintKindChange(event.target.value)}>{SHACL_CONSTRAINTS.map((constraint) => <option key={constraint.value} value={constraint.value}>{constraint.label}</option>)}</select></label>
          <label>Constraint value<input value={props.constraintValue} onChange={(event) => props.onConstraintValueChange(event.target.value)} placeholder="1" /></label>
          <label>Severity<select value={props.severity} onChange={(event) => props.onSeverityChange(event.target.value)}><option>Violation</option><option>Warning</option><option>Info</option></select></label>
          <label className="shacl-message-field">Validation message<input value={props.validationMessage} onChange={(event) => props.onValidationMessageChange(event.target.value)} placeholder={`Explain the ${props.entity.label} constraint`} /></label>
          <button className="button small primary" type="submit" disabled={props.pending || !props.shapeLabel.trim() || props.target.length === 0 || props.path.length === 0 || !props.constraintValue.trim()}>Add SHACL constraint</button>
        </form>
      </div>
    </EditableFactSection>
  </div>;
}

function EditableFactSection({ title, values, unavailable, children }: { title: string; values: string[]; unavailable?: string; children?: React.ReactNode }) {
  return <section className="editable-fact-section" aria-labelledby={`${slug(title)}-heading`}>
    <div className="editable-fact-heading">
      <h3 id={`${slug(title)}-heading`}>{title}</h3>
      <FactValues values={values} />
    </div>
    {unavailable ? <p className="fact-guidance">{unavailable}</p> : children}
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

function EditorTab({ id, label, active, onSelect }: { id: EditorSectionId; label: string; active: EditorSectionId; onSelect: (id: EditorSectionId) => void }) {
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

function stagedEntryKey(entry: WebStagedEntry) {
  return [
    entry.editType,
    entry.normalizedValues.classIri ?? entry.normalizedValues.propertyIri,
    entry.normalizedValues.superclassIri
      ?? entry.normalizedValues.domainClassIri
      ?? entry.normalizedValues.rangeIri
      ?? entry.normalizedValues.rangeLabel,
  ].join("|");
}

function readableDatatype(reference: WebEntityReference) {
  return reference.iri.split(/[#/]/).filter(Boolean).at(-1) ?? reference.label;
}

function relationshipLabels(relationships: WebRelationship[]) {
  return relationships
    .filter((relationship) => !STRUCTURAL_PREDICATES.has(relationship.predicate.iri))
    .map((relationship) => `${relationship.predicate.label} → ${formatValue(relationship.value)}`);
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
      kind: null,
      sourceId: relationship.sourceId,
    }));
  return [...byIri.values()].sort((left, right) => left.label.localeCompare(right.label));
}

function formatValue(value: WebRdfValue) {
  return value.label ?? KNOWN_RESOURCE_LABELS.get(value.value) ?? readableIriLabel(value.value);
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
const RDFS_DOMAIN = "http://www.w3.org/2000/01/rdf-schema#domain";
const RDFS_RANGE = "http://www.w3.org/2000/01/rdf-schema#range";
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
