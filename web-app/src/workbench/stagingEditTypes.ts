import type { WebStageChangeRequest } from "../web/projectApi";

export type WebStagingEditType =
  | "create-class"
  | "set-entity-label"
  | "add-superclass"
  | "remove-superclass"
  | "create-object-property"
  | "create-datatype-property"
  | "set-property-domain"
  | "set-property-range"
  | "create-individual"
  | "assign-type"
  | "add-object-property-assertion"
  | "add-datatype-property-assertion"
  | "shacl-create-node-shape"
  | "shacl-create-property-shape"
  | "shacl-update-constraint"
  | "shacl-remove-constraint"
  | "shacl-delete-shape";

export type StagingField =
  | "classLabel"
  | "superclassLabel"
  | "propertyLabel"
  | "domainClassLabel"
  | "rangeLabel"
  | "individualLabel"
  | "resourceLabel"
  | "typeLabel"
  | "subjectLabel"
  | "objectLabel"
  | "shapeLabel"
  | "targetClassLabel"
  | "pathLabel"
  | "constraintKind"
  | "constraintValue"
  | "severity"
  | "validationMessage"
  | "label"
  | "value";

export type StagingFormValues = Partial<Record<StagingField, string>>;

export interface StagingFieldDefinition {
  key: StagingField;
  label: string;
  placeholder: string;
  required: boolean;
}

export interface StagingEditDefinition {
  type: WebStagingEditType;
  label: string;
  description: string;
  fields: readonly StagingFieldDefinition[];
}

const text = (key: StagingField, label: string, placeholder: string, required = true): StagingFieldDefinition => ({
  key,
  label,
  placeholder,
  required,
});

export const STAGING_EDIT_DEFINITIONS: readonly StagingEditDefinition[] = [
  {
    type: "create-class",
    label: "Create class",
    description: "Create a named OWL class. Entio generates its IRI from the label when the change is staged.",
    fields: [text("label", "Class label", "Account")],
  },
  {
    type: "set-entity-label",
    label: "Set entity label",
    description: "Set the preferred human-readable label for an existing entity.",
    fields: [text("resourceLabel", "Current entity label", "Account"), text("label", "New label", "Customer account")],
  },
  {
    type: "add-superclass",
    label: "Add superclass",
    description: "Assert that one class is a direct subclass of another class.",
    fields: [text("classLabel", "Class label", "Checking Account"), text("superclassLabel", "Superclass label", "Account")],
  },
  {
    type: "remove-superclass",
    label: "Remove superclass",
    description: "Remove an asserted direct superclass relationship.",
    fields: [text("classLabel", "Class label", "Checking Account"), text("superclassLabel", "Superclass label", "Account")],
  },
  {
    type: "create-object-property",
    label: "Create object property",
    description: "Create a property whose values are ontology resources.",
    fields: [text("label", "Property label", "owns account")],
  },
  {
    type: "create-datatype-property",
    label: "Create datatype property",
    description: "Create a property whose values are RDF literals.",
    fields: [text("label", "Property label", "account number")],
  },
  {
    type: "set-property-domain",
    label: "Set property domain",
    description: "Set the class used as the asserted domain of a property.",
    fields: [text("propertyLabel", "Property label", "owns account"), text("domainClassLabel", "Domain class label", "Customer")],
  },
  {
    type: "set-property-range",
    label: "Set property range",
    description: "Set the asserted class or standard datatype range of a property.",
    fields: [text("propertyLabel", "Property label", "owns account"), text("rangeLabel", "Range label", "Account or string")],
  },
  {
    type: "create-individual",
    label: "Create individual",
    description: "Create a named individual and assign its required initial class.",
    fields: [text("label", "Individual label", "Shrey"), text("classLabel", "Class", "Customer")],
  },
  {
    type: "assign-type",
    label: "Assign type",
    description: "Assert that an existing resource is an instance of a class.",
    fields: [text("resourceLabel", "Resource label", "Shrey"), text("typeLabel", "Type label", "Customer")],
  },
  {
    type: "add-object-property-assertion",
    label: "Add object relationship",
    description: "Connect two existing resources with an object property.",
    fields: [
      text("subjectLabel", "Subject label", "Shrey"),
      text("propertyLabel", "Object property label", "owns account"),
      text("objectLabel", "Object label", "Checking Account 1"),
    ],
  },
  {
    type: "add-datatype-property-assertion",
    label: "Add datatype value",
    description: "Attach a string literal value to an existing resource.",
    fields: [
      text("subjectLabel", "Subject label", "Checking Account 1"),
      text("propertyLabel", "Datatype property label", "account number"),
      text("value", "Value", "20874"),
    ],
  },
  {
    type: "shacl-create-node-shape",
    label: "Create node shape",
    description: "Create a typed SHACL node shape in a registered shapes source.",
    fields: [
      text("shapeLabel", "Shape label", "Customer shape"),
      text("targetClassLabel", "Target class label", "Customer"),
      text("severity", "Severity", "Violation", false),
      text("validationMessage", "Validation message", "Customer data must satisfy this shape.", false),
    ],
  },
  {
    type: "shacl-create-property-shape",
    label: "Create property shape",
    description: "Create a direct-property SHACL constraint and preview its finding impact before approval.",
    fields: [
      text("shapeLabel", "Shape label", "Customer account shape"),
      text("targetClassLabel", "Target class label", "Customer"),
      text("pathLabel", "Property path label", "owns account"),
      text("constraintKind", "Constraint", "min-count"),
      text("constraintValue", "Constraint value", "1"),
      text("severity", "Severity", "Violation", false),
      text("validationMessage", "Validation message", "Each customer must own an account.", false),
    ],
  },
  {
    type: "shacl-update-constraint",
    label: "Update SHACL constraint",
    description: "Replace one supported constraint on an existing direct property shape.",
    fields: [
      text("shapeLabel", "Shape label", "Customer account shape"),
      text("pathLabel", "Property path label", "owns account"),
      text("constraintKind", "Constraint", "min-count"),
      text("constraintValue", "New value", "1"),
    ],
  },
  {
    type: "shacl-remove-constraint",
    label: "Remove SHACL constraint",
    description: "Remove one supported constraint from an existing direct property shape.",
    fields: [
      text("shapeLabel", "Shape label", "Customer account shape"),
      text("pathLabel", "Property path label", "owns account"),
      text("constraintKind", "Constraint", "min-count"),
    ],
  },
  {
    type: "shacl-delete-shape",
    label: "Delete SHACL shape",
    description: "Delete a supported shape only after its dependencies and finding impact are reviewed.",
    fields: [text("shapeLabel", "Shape label", "Customer account shape")],
  },
];

export function stagingEditDefinition(type: WebStagingEditType): StagingEditDefinition {
  return STAGING_EDIT_DEFINITIONS.find((definition) => definition.type === type) ?? STAGING_EDIT_DEFINITIONS[0];
}

export function buildStageChangeRequest(
  sourceId: string,
  type: WebStagingEditType,
  values: StagingFormValues,
  idempotencyKey: string,
): WebStageChangeRequest {
  const definition = stagingEditDefinition(type);
  const missing = definition.fields.find((field) => field.required && !values[field.key]?.trim());
  if (missing) throw new Error(`${missing.label} is required.`);

  const fields = Object.fromEntries(
    definition.fields
      .map((field) => [field.key, values[field.key]?.trim()] as const)
      .filter((entry): entry is readonly [StagingField, string] => Boolean(entry[1])),
  );
  return { sourceId, editType: type, ...fields, idempotencyKey };
}
