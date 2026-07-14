import type { EngineResponse } from "./engineCli";

export interface OntologySourceSummary {
  readonly id: string;
  readonly path: string;
  readonly format: string;
  readonly tripleCount: number;
}

export interface SymbolSummary {
  readonly iri: string;
  readonly label: string | null;
  readonly kind: string;
  readonly sourceId: string;
  readonly relationships: readonly SymbolRelationshipSummary[];
}

export interface RdfTermSummary {
  readonly kind: string;
  readonly value: string;
  readonly datatype: string | null;
  readonly language: string | null;
}

export interface LocalizedTextSummary {
  readonly value: string;
  readonly language: string | null;
  readonly datatype: string | null;
}

export interface SemanticAnnotationSummary {
  readonly subject: string;
  readonly property: string;
  readonly value: RdfTermSummary;
  readonly sourceId: string;
}

export interface SemanticObjectAssertionSummary {
  readonly subject: string;
  readonly property: string;
  readonly value: string;
  readonly sourceId: string;
}

export interface SemanticDatatypeAssertionSummary {
  readonly subject: string;
  readonly property: string;
  readonly value: RdfTermSummary;
  readonly sourceId: string;
}

export interface SemanticDescriptorSummary {
  readonly iri: string;
  readonly kind: string;
  readonly sourceId: string;
  readonly sourceOntologyId: string;
  readonly locality: string;
  readonly preferredLabelSource: string;
  readonly preferredLabel: LocalizedTextSummary | null;
  readonly ambiguousPreferredLabelLanguages: readonly string[];
  readonly alternateLabels: readonly LocalizedTextSummary[];
  readonly definitions: readonly LocalizedTextSummary[];
  readonly annotations: readonly SemanticAnnotationSummary[];
  readonly directSuperclasses: readonly string[];
  readonly directSubclasses: readonly string[];
  readonly directlyTypedIndividuals: readonly string[];
  readonly domains: readonly string[];
  readonly ranges: readonly string[];
  readonly datatypeRanges: readonly string[];
  readonly statementsUsingProperty: readonly SemanticAnnotationSummary[];
  readonly assertedTypes: readonly string[];
  readonly directAssertions: readonly (SemanticObjectAssertionSummary | SemanticDatatypeAssertionSummary)[];
  readonly objectPropertyAssertions: readonly SemanticObjectAssertionSummary[];
  readonly datatypePropertyAssertions: readonly SemanticDatatypeAssertionSummary[];
}

export interface SemanticSearchResultSummary {
  readonly reason: string;
  readonly rank: number;
  readonly descriptor: SemanticDescriptorSummary;
}

export interface SemanticSearchSummary {
  readonly query: string;
  readonly ambiguous: boolean;
  readonly results: readonly SemanticSearchResultSummary[];
}

export interface SymbolRelationshipSummary {
  readonly direction: "outgoing" | "incoming";
  readonly kind: "type" | "property";
  readonly predicate: string;
  readonly predicateLabel: string | null;
  readonly value: RdfTermSummary;
  readonly valueLabel: string | null;
  readonly sourceId: string;
}

export interface SymbolGroup {
  readonly kind: string;
  readonly symbols: readonly SymbolSummary[];
}

export interface WorkbenchModel {
  readonly projectName: string;
  readonly projectRoot: string;
  readonly graphTripleCount: number;
  readonly ontologySources: readonly OntologySourceSummary[];
  readonly symbolGroups: readonly SymbolGroup[];
  readonly selectedSymbol?: SymbolSummary;
}

export function createWorkbenchModel(response: EngineResponse): WorkbenchModel | undefined {
  if (!response.ok) {
    return undefined;
  }

  const project = asRecord(response.project);
  const ontologySources = Array.isArray(response.ontologySources)
    ? response.ontologySources.map(readSource).filter(isDefined)
    : [];
  const detailsByIri = new Map<string, readonly SymbolRelationshipSummary[]>();
  if (Array.isArray(response.symbolDetails)) {
    response.symbolDetails.forEach((value) => {
      const details = readSymbolDetails(value);
      if (details) detailsByIri.set(details.iri, details.relationships);
    });
  }
  const symbols = Array.isArray(response.symbols)
    ? response.symbols.map((value) => readSymbol(value, detailsByIri)).filter(isDefined)
    : [];

  if (!project || typeof project.name !== "string" || typeof project.root !== "string") {
    return undefined;
  }

  const graphTripleCount = numberValue(project.graphTripleCount);
  if (graphTripleCount === undefined) {
    return undefined;
  }

  return {
    projectName: project.name,
    projectRoot: project.root,
    graphTripleCount,
    ontologySources: ontologySources.sort(compareSources),
    symbolGroups: groupSymbols(symbols),
  };
}

export function selectSymbol(model: WorkbenchModel, iri: string): WorkbenchModel {
  const selectedSymbol = model.symbolGroups
    .flatMap((group) => group.symbols)
    .find((symbol) => symbol.iri === iri);

  return selectedSymbol
    ? { ...model, selectedSymbol }
    : { ...model, selectedSymbol: undefined };
}

/** Returns label-backed display options; final resolution remains owned by the Kotlin CLI. */
export function entitySelectorOptions(
  model: WorkbenchModel,
  kind?: string,
  sourceId?: string,
): readonly SymbolSummary[] {
  return model.symbolGroups
    .flatMap((group) => group.symbols)
    .filter((symbol) => !kind || symbol.kind === kind)
    .filter((symbol) => !sourceId || symbol.sourceId === sourceId)
    .sort(compareSymbols);
}

export function labelDisplay(symbol: SymbolSummary): string {
  return symbol.label ? `${symbol.label} · ${symbol.kind} · ${symbol.sourceId}` : `${symbol.iri} · ${symbol.kind} · ${symbol.sourceId}`;
}

export function createSemanticDescriptorModel(response: unknown): SemanticDescriptorSummary | undefined {
  const record = asRecord(response);
  if (!record || record.ok !== true) return undefined;
  return readSemanticDescriptor(record.descriptor);
}

export function createSemanticSearchModel(response: unknown): SemanticSearchSummary | undefined {
  const record = asRecord(response);
  if (
    !record ||
    record.ok !== true ||
    typeof record.query !== "string" ||
    typeof record.ambiguous !== "boolean" ||
    !Array.isArray(record.results)
  ) {
    return undefined;
  }

  const results = record.results.map(readSemanticSearchResult).filter(isDefined);
  return {
    query: record.query,
    ambiguous: record.ambiguous,
    results,
  };
}

function groupSymbols(symbols: readonly SymbolSummary[]): readonly SymbolGroup[] {
  const groups = new Map<string, SymbolSummary[]>();

  [...symbols].sort(compareSymbols).forEach((symbol) => {
    const group = groups.get(symbol.kind) ?? [];
    group.push(symbol);
    groups.set(symbol.kind, group);
  });

  return [...groups.entries()]
    .sort(([firstKind], [secondKind]) => compareKinds(firstKind, secondKind))
    .map(([kind, groupedSymbols]) => ({ kind, symbols: groupedSymbols }));
}

function readSource(value: unknown): OntologySourceSummary | undefined {
  const source = asRecord(value);
  if (
    !source ||
    typeof source.id !== "string" ||
    typeof source.path !== "string" ||
    typeof source.format !== "string"
  ) {
    return undefined;
  }

  const tripleCount = numberValue(source.tripleCount);
  return tripleCount === undefined
    ? undefined
    : { id: source.id, path: source.path, format: source.format, tripleCount };
}

function readSymbol(
  value: unknown,
  detailsByIri: Map<string, readonly SymbolRelationshipSummary[]> = new Map(),
): SymbolSummary | undefined {
  const symbol = asRecord(value);
  if (
    !symbol ||
    typeof symbol.iri !== "string" ||
    typeof symbol.kind !== "string" ||
    typeof symbol.sourceId !== "string"
  ) {
    return undefined;
  }

  return {
    iri: symbol.iri,
    label: typeof symbol.label === "string" ? symbol.label : null,
    kind: symbol.kind,
    sourceId: symbol.sourceId,
    relationships: detailsByIri.get(symbol.iri) ?? [],
  };
}

function readSymbolDetails(value: unknown): SymbolSummary | undefined {
  const details = asRecord(value);
  if (!details || !Array.isArray(details.relationships)) return undefined;

  const relationships = details.relationships.map(readRelationship).filter(isDefined);
  return readSymbol(details, new Map([[typeof details.iri === "string" ? details.iri : "", relationships]]));
}

function readRelationship(value: unknown): SymbolRelationshipSummary | undefined {
  const relationship = asRecord(value);
  const term = relationship ? readRdfTerm(relationship.value) : undefined;
  if (
    !relationship ||
    (relationship.direction !== "outgoing" && relationship.direction !== "incoming") ||
    (relationship.kind !== "type" && relationship.kind !== "property") ||
    typeof relationship.predicate !== "string" ||
    !term ||
    typeof relationship.sourceId !== "string"
  ) {
    return undefined;
  }

  return {
    direction: relationship.direction,
    kind: relationship.kind,
    predicate: relationship.predicate,
    predicateLabel: typeof relationship.predicateLabel === "string" ? relationship.predicateLabel : null,
    value: term,
    valueLabel: typeof relationship.valueLabel === "string" ? relationship.valueLabel : null,
    sourceId: relationship.sourceId,
  };
}

function readSemanticDescriptor(value: unknown): SemanticDescriptorSummary | undefined {
  const descriptor = asRecord(value);
  if (
    !descriptor ||
    typeof descriptor.iri !== "string" ||
    typeof descriptor.kind !== "string" ||
    typeof descriptor.sourceId !== "string" ||
    typeof descriptor.sourceOntologyId !== "string" ||
    typeof descriptor.locality !== "string" ||
    typeof descriptor.preferredLabelSource !== "string"
  ) {
    return undefined;
  }

  const preferredLabel = readLocalizedText(descriptor.preferredLabel);
  const alternateLabels = readLocalizedTexts(descriptor.alternateLabels);
  const definitions = readLocalizedTexts(descriptor.definitions);
  const annotations = readSemanticAnnotations(descriptor.annotations);
  const statementsUsingProperty = readSemanticAnnotations(descriptor.statementsUsingProperty);
  const directAssertions = readAssertions(descriptor.directAssertions);
  const objectPropertyAssertions = readObjectAssertions(descriptor.objectPropertyAssertions);
  const datatypePropertyAssertions = readDatatypeAssertions(descriptor.datatypePropertyAssertions);

  if (
    !preferredLabel.valid ||
    alternateLabels === undefined ||
    definitions === undefined ||
    annotations === undefined ||
    statementsUsingProperty === undefined ||
    directAssertions === undefined ||
    objectPropertyAssertions === undefined ||
    datatypePropertyAssertions === undefined
  ) {
    return undefined;
  }

  return {
    iri: descriptor.iri,
    kind: descriptor.kind,
    sourceId: descriptor.sourceId,
    sourceOntologyId: descriptor.sourceOntologyId,
    locality: descriptor.locality,
    preferredLabelSource: descriptor.preferredLabelSource,
    preferredLabel: preferredLabel.value,
    ambiguousPreferredLabelLanguages: stringArray(descriptor.ambiguousPreferredLabelLanguages) ?? [],
    alternateLabels,
    definitions,
    annotations,
    directSuperclasses: stringArray(descriptor.directSuperclasses) ?? [],
    directSubclasses: stringArray(descriptor.directSubclasses) ?? [],
    directlyTypedIndividuals: stringArray(descriptor.directlyTypedIndividuals) ?? [],
    domains: stringArray(descriptor.domains) ?? [],
    ranges: stringArray(descriptor.ranges) ?? [],
    datatypeRanges: stringArray(descriptor.datatypeRanges) ?? [],
    statementsUsingProperty,
    assertedTypes: stringArray(descriptor.assertedTypes) ?? [],
    directAssertions,
    objectPropertyAssertions,
    datatypePropertyAssertions,
  };
}

function readSemanticSearchResult(value: unknown): SemanticSearchResultSummary | undefined {
  const result = asRecord(value);
  const descriptor = result ? readSemanticDescriptor(result.descriptor) : undefined;
  const rank = result ? numberValue(result.rank) : undefined;
  if (!result || typeof result.reason !== "string" || rank === undefined || !descriptor) return undefined;
  return { reason: result.reason, rank, descriptor };
}

function readLocalizedText(value: unknown): { value: LocalizedTextSummary | null; valid: boolean } {
  if (value === null || value === undefined) return { value: null, valid: true };
  const text = asRecord(value);
  if (!text || typeof text.value !== "string") return { value: null, valid: false };
  return {
    value: {
      value: text.value,
      language: typeof text.language === "string" ? text.language : null,
      datatype: typeof text.datatype === "string" ? text.datatype : null,
    },
    valid: true,
  };
}

function readLocalizedTexts(value: unknown): readonly LocalizedTextSummary[] | undefined {
  if (!Array.isArray(value)) return [];
  const texts = value.map(readLocalizedText);
  return texts.every((text) => text.valid)
    ? texts.map((text) => text.value).filter((text): text is LocalizedTextSummary => text !== null)
    : undefined;
}

function readSemanticAnnotations(value: unknown): readonly SemanticAnnotationSummary[] | undefined {
  if (!Array.isArray(value)) return [];
  const annotations = value.map((entry) => {
    const annotation = asRecord(entry);
    const term = annotation ? readRdfTerm(annotation.value) : undefined;
    if (
      !annotation ||
      typeof annotation.subject !== "string" ||
      typeof annotation.property !== "string" ||
      typeof annotation.sourceId !== "string" ||
      !term
    ) {
      return undefined;
    }
    return { subject: annotation.subject, property: annotation.property, value: term, sourceId: annotation.sourceId };
  });
  return annotations.every(isDefined) ? annotations : undefined;
}

function readAssertions(value: unknown): readonly (SemanticObjectAssertionSummary | SemanticDatatypeAssertionSummary)[] | undefined {
  if (!Array.isArray(value)) return [];
  const assertions = value.map((entry) => readObjectAssertion(entry) ?? readDatatypeAssertion(entry));
  return assertions.every(isDefined) ? assertions : undefined;
}

function readObjectAssertions(value: unknown): readonly SemanticObjectAssertionSummary[] | undefined {
  if (!Array.isArray(value)) return [];
  const assertions = value.map(readObjectAssertion);
  return assertions.every(isDefined) ? assertions : undefined;
}

function readDatatypeAssertions(value: unknown): readonly SemanticDatatypeAssertionSummary[] | undefined {
  if (!Array.isArray(value)) return [];
  const assertions = value.map(readDatatypeAssertion);
  return assertions.every(isDefined) ? assertions : undefined;
}

function readObjectAssertion(value: unknown): SemanticObjectAssertionSummary | undefined {
  const assertion = asRecord(value);
  if (
    !assertion ||
    typeof assertion.subject !== "string" ||
    typeof assertion.property !== "string" ||
    typeof assertion.value !== "string" ||
    typeof assertion.sourceId !== "string"
  ) {
    return undefined;
  }
  return { subject: assertion.subject, property: assertion.property, value: assertion.value, sourceId: assertion.sourceId };
}

function readDatatypeAssertion(value: unknown): SemanticDatatypeAssertionSummary | undefined {
  const assertion = asRecord(value);
  const term = assertion ? readRdfTerm(assertion.value) : undefined;
  if (
    !assertion ||
    typeof assertion.subject !== "string" ||
    typeof assertion.property !== "string" ||
    !term ||
    typeof assertion.sourceId !== "string"
  ) {
    return undefined;
  }
  return { subject: assertion.subject, property: assertion.property, value: term, sourceId: assertion.sourceId };
}

function stringArray(value: unknown): readonly string[] | undefined {
  return Array.isArray(value) && value.every((entry) => typeof entry === "string")
    ? value
    : undefined;
}

function readRdfTerm(value: unknown): RdfTermSummary | undefined {
  const term = asRecord(value);
  if (
    !term ||
    typeof term.kind !== "string" ||
    typeof term.value !== "string" ||
    (term.datatype !== null && typeof term.datatype !== "string") ||
    (term.language !== null && typeof term.language !== "string")
  ) {
    return undefined;
  }

  return {
    kind: term.kind,
    value: term.value,
    datatype: typeof term.datatype === "string" ? term.datatype : null,
    language: typeof term.language === "string" ? term.language : null,
  };
}

function compareSources(first: OntologySourceSummary, second: OntologySourceSummary): number {
  return compareText(first.id, second.id);
}

function compareSymbols(first: SymbolSummary, second: SymbolSummary): number {
  return compareText(first.kind, second.kind) ||
    compareText(first.label ?? first.iri, second.label ?? second.iri) ||
    compareText(first.iri, second.iri);
}

function compareKinds(first: string, second: string): number {
  const firstRank = symbolKindRank(first);
  const secondRank = symbolKindRank(second);
  return firstRank - secondRank || compareText(first, second);
}

function symbolKindRank(kind: string): number {
  const order = ["Class", "Property", "Individual", "Shape", "NamespaceTerm", "Unknown"];
  const index = order.indexOf(kind);
  return index === -1 ? order.length : index;
}

function compareText(first: string, second: string): number {
  return first < second ? -1 : first > second ? 1 : 0;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null
    ? value as Record<string, unknown>
    : undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function isDefined<T>(value: T | undefined): value is T {
  return value !== undefined;
}
