export interface EntityKindPresentation {
  marker: "C" | "O" | "P";
  label: string;
  className: string;
}

export function entityKindPresentation(kind: string | null | undefined): EntityKindPresentation {
  switch (kind?.toLowerCase()) {
    case "class":
      return { marker: "C", label: "Class", className: "entity-type-class" };
    case "objectproperty":
      return { marker: "P", label: "Object property", className: "entity-type-property" };
    case "datatypeproperty":
      return { marker: "P", label: "Datatype property", className: "entity-type-property" };
    case "annotationproperty":
      return { marker: "P", label: "Annotation property", className: "entity-type-property" };
    case "individual":
      return { marker: "O", label: "Object", className: "entity-type-object" };
    default:
      return { marker: "O", label: "Object", className: "entity-type-object" };
  }
}
