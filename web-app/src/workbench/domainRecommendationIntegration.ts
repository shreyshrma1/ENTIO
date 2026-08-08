import type { DomainEntityKind, WebDomainOperationKind } from "../web/projectApi";
import type { StagingField, WebStagingEditType } from "./stagingEditTypes";

export interface CoreAuthoringRecommendationRow {
  slice: 8;
  workflow: string;
  operationKind: WebDomainOperationKind;
  requestedKind: DomainEntityKind;
  intentFields: readonly string[];
  component: string;
  acceptance: string;
}

/** Executable inventory preventing a core authoring surface from silently missing Phase 13 retrieval. */
export const CORE_AUTHORING_RECOMMENDATION_MATRIX: readonly CoreAuthoringRecommendationRow[] = [
  { slice: 8, workflow: "create-class", operationKind: "CreateClass", requestedKind: "Class", intentFields: ["draftLabel", "definition", "requiredParentIri", "targetSourceId"], component: "ClassEditDialog", acceptance: "class kind only; local form preserved" },
  { slice: 8, workflow: "create-object-property", operationKind: "CreateObjectProperty", requestedKind: "ObjectProperty", intentFields: ["draftLabel", "requiredDomainIri", "requiredRangeIri", "targetSourceId"], component: "TypedEditDialog/ClassPropertyDialog", acceptance: "object properties only" },
  { slice: 8, workflow: "create-datatype-property", operationKind: "CreateDatatypeProperty", requestedKind: "DatatypeProperty", intentFields: ["draftLabel", "requiredDomainIri", "requiredDatatypeIri", "targetSourceId"], component: "TypedEditDialog/ClassPropertyDialog", acceptance: "datatype properties only" },
  { slice: 8, workflow: "individual-type", operationKind: "CreateIndividualTypeSelection", requestedKind: "Class", intentFields: ["draftLabel", "currentEntityIri", "targetSourceId"], component: "SemanticClassPicker", acceptance: "reference individuals are never imported" },
  { slice: 8, workflow: "label-definition", operationKind: "EditLabelOrDefinition", requestedKind: "Class", intentFields: ["draftLabel", "alternateWording", "definition", "currentEntityIri", "targetSourceId"], component: "OverviewTab", acceptance: "source and project wording remain distinct" },
  { slice: 8, workflow: "class-hierarchy", operationKind: "EditClassHierarchy", requestedKind: "Class", intentFields: ["draftLabel", "currentEntityIri", "requiredParentIri", "targetSourceId"], component: "SemanticClassPicker", acceptance: "server resolves hierarchy context" },
  { slice: 8, workflow: "property-hierarchy", operationKind: "EditPropertyHierarchy", requestedKind: "ObjectProperty", intentFields: ["draftLabel", "currentEntityIri", "targetSourceId"], component: "DomainRecommendationPanel", acceptance: "strict property kind; advisory when no typed local operation exists" },
  { slice: 8, workflow: "domain", operationKind: "EditDomain", requestedKind: "Class", intentFields: ["draftLabel", "currentEntityIri", "targetSourceId"], component: "SemanticClassPicker", acceptance: "browser does not calculate compatibility" },
  { slice: 8, workflow: "range", operationKind: "EditRangeOrDatatype", requestedKind: "Class", intentFields: ["draftLabel", "currentEntityIri", "requiredDomainIri", "targetSourceId"], component: "SemanticClassPicker", acceptance: "server resolves range context" },
  { slice: 8, workflow: "datatype", operationKind: "EditRangeOrDatatype", requestedKind: "DatatypeProperty", intentFields: ["draftLabel", "currentEntityIri", "requiredDatatypeIri", "targetSourceId"], component: "DomainRecommendationPanel", acceptance: "standard datatype remains an explicit local choice" },
  { slice: 8, workflow: "object-assertion", operationKind: "AddAssertionOrValue", requestedKind: "ObjectProperty", intentFields: ["draftLabel", "currentEntityIri", "nearbyProjectIris", "targetSourceId"], component: "SemanticEntityPicker", acceptance: "property and target context are server checked" },
  { slice: 8, workflow: "datatype-value", operationKind: "AddAssertionOrValue", requestedKind: "DatatypeProperty", intentFields: ["draftLabel", "currentEntityIri", "requiredDatatypeIri", "targetSourceId"], component: "SemanticEntityPicker", acceptance: "literal value remains local and unchanged" },
] as const;

export interface TypedEditRecommendationConfig {
  operationKind: WebDomainOperationKind;
  requestedKind: DomainEntityKind;
  draftField: StagingField;
}

export const REMAINING_WORKFLOW_RECOMMENDATION_MATRIX = [
  { slice: 9, workflow: "deletion-replacement", operationKind: "DeleteOrReplaceEntity", component: "DeletionDialog", invariant: "deletion dependencies unchanged" },
  { slice: 9, workflow: "proposal-reuse-review", operationKind: "ProposalReuseReview", component: "ProposalDetailsDialog", invariant: "conversion invalidates proposal preview" },
  { slice: 9, workflow: "shacl-target", operationKind: "ShaclTargetClass", component: "ShaclConstraintDialog", invariant: "validation remains Kotlin-owned" },
  { slice: 9, workflow: "shacl-path", operationKind: "ShaclPropertyPath", component: "ShaclConstraintDialog", invariant: "strict property kind" },
  { slice: 9, workflow: "shacl-constraint", operationKind: "ShaclClassOrDatatypeConstraint", component: "ShaclConstraintDialog", invariant: "standard XSD values remain local choices" },
  { slice: 9, workflow: "map-related", operationKind: "OntologyMapRelatedSearch", component: "OntologyMapShell", invariant: "available results never enter layout" },
  { slice: 9, workflow: "reasoning-related", operationKind: "ReasoningWorkspaceRelatedSearch", component: "SemanticJobPanel", invariant: "asserted and inferred facts stay read-only" },
] as const;

export function domainKindForEntityKind(kind: string | null | undefined): DomainEntityKind | null {
  const normalized = kind?.toLowerCase().replaceAll(/[^a-z]/g, "");
  if (normalized === "class") return "Class";
  if (normalized === "objectproperty" || normalized === "property") return "ObjectProperty";
  if (normalized === "datatypeproperty") return "DatatypeProperty";
  return null;
}

export function recommendationConfigForEdit(editType: WebStagingEditType): TypedEditRecommendationConfig | null {
  switch (editType) {
    case "create-class": return { operationKind: "CreateClass", requestedKind: "Class", draftField: "label" };
    case "create-object-property": return { operationKind: "CreateObjectProperty", requestedKind: "ObjectProperty", draftField: "label" };
    case "create-datatype-property": return { operationKind: "CreateDatatypeProperty", requestedKind: "DatatypeProperty", draftField: "label" };
    case "create-individual": return { operationKind: "CreateIndividualTypeSelection", requestedKind: "Class", draftField: "classLabel" };
    case "add-superclass":
    case "remove-superclass": return { operationKind: "EditClassHierarchy", requestedKind: "Class", draftField: "superclassLabel" };
    case "set-property-domain": return { operationKind: "EditDomain", requestedKind: "Class", draftField: "domainClassLabel" };
    case "set-property-range": return { operationKind: "EditRangeOrDatatype", requestedKind: "Class", draftField: "rangeLabel" };
    case "assign-type": return { operationKind: "CreateIndividualTypeSelection", requestedKind: "Class", draftField: "typeLabel" };
    case "add-object-property-assertion": return { operationKind: "AddAssertionOrValue", requestedKind: "ObjectProperty", draftField: "propertyLabel" };
    case "add-datatype-property-assertion": return { operationKind: "AddAssertionOrValue", requestedKind: "DatatypeProperty", draftField: "propertyLabel" };
    default: return null;
  }
}
