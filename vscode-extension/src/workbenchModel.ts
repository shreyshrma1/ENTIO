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

export interface ReasoningFactSummary {
  readonly subject: string;
  readonly predicate?: string;
  readonly object: string;
  readonly origin: string;
  readonly sourceId: string | null;
}

export interface ReasoningFeatureSummary {
  readonly feature: string;
  readonly support: string;
  readonly affectsCompleteness: boolean;
  readonly message: string | null;
}

export interface ReasoningViewModel {
  readonly status: string;
  readonly consistency: string;
  readonly importClosureComplete: boolean;
  readonly fingerprints: Readonly<Record<string, string>>;
  readonly classRelationships: readonly ReasoningFactSummary[];
  readonly individualTypes: readonly ReasoningFactSummary[];
  readonly propertyRelationships: readonly ReasoningFactSummary[];
  readonly unsatisfiableClasses: readonly string[];
  readonly unsupportedFeatures: readonly ReasoningFeatureSummary[];
  readonly warnings: readonly string[];
  readonly errors: readonly string[];
}

export interface ShaclResultSummary {
  readonly resultId: string;
  readonly severity: string;
  readonly message: string;
  readonly focusNode: string;
  readonly path: string | null;
  readonly shape: string;
  readonly constraint: string;
  readonly value: string | null;
  readonly sourceId: string | null;
}

export interface ShaclValidationViewModel {
  readonly status: string;
  readonly mode: string;
  readonly dataGraphFingerprint: string;
  readonly shapesGraphFingerprint: string;
  readonly results: readonly ShaclResultSummary[];
  readonly warnings: readonly string[];
  readonly errors: readonly string[];
}

export interface ShaclShapeSummary {
  readonly iri: string;
  readonly sourceId: string;
  readonly targets: readonly string[];
  readonly propertyShapes: readonly string[];
  readonly constraints: readonly string[];
}

export interface ShaclShapesViewModel {
  readonly shapes: readonly ShaclShapeSummary[];
}

export interface ProposalImpactViewModel {
  readonly status: string;
  readonly explicitDiffCount: number;
  readonly addedInferences: readonly string[];
  readonly removedInferences: readonly string[];
  readonly newShaclResults: readonly string[];
  readonly worsenedShaclResults: readonly string[];
  readonly unchangedShaclResults: readonly string[];
  readonly resolvedShaclResults: readonly string[];
  readonly blockingMessages: readonly string[];
}

export function createReasoningModel(response: unknown): ReasoningViewModel | undefined {
  const root = asRecord(response);
  const reasoning = root ? asRecord(root.reasoning) : undefined;
  if (!root || root.ok !== true || !reasoning || typeof reasoning.status !== "string" ||
      typeof reasoning.consistency !== "string" || typeof reasoning.importClosureComplete !== "boolean") {
    return undefined;
  }
  const fingerprints = asRecord(reasoning.fingerprints);
  if (!fingerprints) return undefined;
  const readFacts = (value: unknown, subjectKey: string, objectKey: string): readonly ReasoningFactSummary[] => {
    if (!Array.isArray(value)) return [];
    return value.map((entry) => {
      const fact = asRecord(entry);
      if (!fact || typeof fact[subjectKey] !== "string" || typeof fact[objectKey] !== "string" || typeof fact.origin !== "string") return undefined;
      return {
        subject: fact[subjectKey] as string,
        predicate: typeof fact.predicate === "string" ? fact.predicate : undefined,
        object: fact[objectKey] as string,
        origin: fact.origin as string,
        sourceId: typeof fact.sourceId === "string" ? fact.sourceId : null,
      };
    }).filter(isDefined);
  };
  const features = Array.isArray(reasoning.unsupportedFeatures)
    ? reasoning.unsupportedFeatures.map((entry) => {
      const feature = asRecord(entry);
      if (!feature || typeof feature.feature !== "string" || typeof feature.support !== "string" || typeof feature.affectsCompleteness !== "boolean") return undefined;
      return {
        feature: feature.feature,
        support: feature.support,
        affectsCompleteness: feature.affectsCompleteness,
        message: typeof feature.message === "string" ? feature.message : null,
      };
    }).filter(isDefined)
    : [];
  return {
    status: reasoning.status,
    consistency: reasoning.consistency,
    importClosureComplete: reasoning.importClosureComplete,
    fingerprints: Object.fromEntries(Object.entries(fingerprints).filter((entry): entry is [string, string] => typeof entry[1] === "string")),
    classRelationships: readFacts(reasoning.classRelationships, "subject", "objectClass"),
    individualTypes: readFacts(reasoning.individualTypes, "individual", "type"),
    propertyRelationships: readFacts(reasoning.propertyRelationships, "subject", "objectResource"),
    unsatisfiableClasses: stringArray(reasoning.unsatisfiableClasses) ?? [],
    unsupportedFeatures: features,
    warnings: stringArray(reasoning.warnings) ?? [],
    errors: stringArray(reasoning.errors) ?? [],
  };
}

export function createShaclValidationModel(response: unknown): ShaclValidationViewModel | undefined {
  const root = asRecord(response);
  const validation = root ? asRecord(root.validation) : undefined;
  const identity = validation ? asRecord(validation.graphIdentity) : undefined;
  if (!root || typeof root.ok !== "boolean" || !validation || !identity ||
      typeof validation.status !== "string" || typeof validation.mode !== "string" ||
      typeof identity.dataGraphFingerprint !== "string" || typeof identity.shapesGraphFingerprint !== "string") return undefined;
  const results = Array.isArray(validation.results) ? validation.results.map((entry) => {
    const result = asRecord(entry);
    const path = result ? asRecord(result.path) : undefined;
    if (!result || typeof result.resultId !== "string" || typeof result.severity !== "string" ||
        typeof result.message !== "string" || typeof result.focusNode !== "string" ||
        typeof result.shape !== "string" || typeof result.constraint !== "string") return undefined;
    return {
      resultId: result.resultId,
      severity: result.severity,
      message: result.message,
      focusNode: result.focusNode,
      path: path && typeof path.iri === "string" ? path.iri : null,
      shape: result.shape,
      constraint: result.constraint,
      value: typeof result.value === "string" ? result.value : null,
      sourceId: typeof result.sourceId === "string" ? result.sourceId : null,
    };
  }).filter(isDefined) : [];
  return {
    status: validation.status,
    mode: validation.mode,
    dataGraphFingerprint: identity.dataGraphFingerprint,
    shapesGraphFingerprint: identity.shapesGraphFingerprint,
    results,
    warnings: stringArray(validation.warnings) ?? [],
    errors: stringArray(validation.errors) ?? [],
  };
}

export function createShaclShapesModel(response: unknown): ShaclShapesViewModel | undefined {
  const root = asRecord(response);
  if (!root || root.ok !== true || !Array.isArray(root.shapes)) return undefined;
  const shapes = root.shapes.map((entry) => {
    const shape = asRecord(entry);
    if (!shape || typeof shape.iri !== "string" || typeof shape.sourceId !== "string") return undefined;
    const targets = Array.isArray(shape.targets) ? shape.targets.map((target) => {
      const value = asRecord(target);
      if (!value) return undefined;
      return typeof value.iri === "string" ? value.iri : typeof value.value === "string" ? value.value : undefined;
    }).filter(isDefined) : [];
    const propertyShapes = Array.isArray(shape.propertyShapes) ? shape.propertyShapes.map((property) => {
      const value = asRecord(property);
      const path = value ? asRecord(value.path) : undefined;
      return path && typeof path.iri === "string" ? path.iri : undefined;
    }).filter(isDefined) : [];
    const constraints = Array.isArray(shape.constraints) ? shape.constraints.map((constraint) => {
      const value = asRecord(constraint);
      return value && typeof value.kind === "string" ? value.kind : undefined;
    }).filter(isDefined) : [];
    return { iri: shape.iri, sourceId: shape.sourceId, targets, propertyShapes, constraints };
  }).filter(isDefined);
  return { shapes };
}

export function createProposalImpactModel(response: unknown): ProposalImpactViewModel | undefined {
  const root = asRecord(response);
  const impact = root ? asRecord(root.impact) : undefined;
  const explicit = impact ? asRecord(impact.explicitDiff) : undefined;
  const reasoning = impact ? asRecord(impact.reasoningImpact) : undefined;
  const shacl = impact ? asRecord(impact.shaclImpact) : undefined;
  if (!root || typeof root.ok !== "boolean" || !impact || typeof impact.status !== "string" || !explicit || !reasoning || !shacl) return undefined;
  const relationshipSummary = (value: unknown): readonly string[] => Array.isArray(value) ? value.map((entry) => {
    const relationship = asRecord(entry);
    if (!relationship) return undefined;
    const subject = typeof relationship.subject === "string" ? relationship.subject : "";
    const predicate = typeof relationship.predicate === "string" ? relationship.predicate : "";
    const object = typeof relationship.objectResource === "string" ? relationship.objectResource : "";
    return subject && predicate && object ? `${displayLocalName(subject)} · ${displayLocalName(predicate)} · ${displayLocalName(object)}` : undefined;
  }).filter(isDefined) : [];
  const resultSummary = (value: unknown): readonly string[] => Array.isArray(value) ? value.map((entry) => {
    const result = asRecord(entry);
    return result && typeof result.message === "string" ? `${result.severity || "result"} · ${result.message}` : undefined;
  }).filter(isDefined) : [];
  const diffEntries = asRecord(explicit)?.entries;
  return {
    status: impact.status,
    explicitDiffCount: Array.isArray(diffEntries) ? diffEntries.length : 0,
    addedInferences: relationshipSummary(reasoning.addedInferences),
    removedInferences: relationshipSummary(reasoning.removedInferences),
    newShaclResults: resultSummary(shacl.newResults),
    worsenedShaclResults: resultSummary(shacl.worsenedResults),
    unchangedShaclResults: resultSummary(shacl.unchangedResults),
    resolvedShaclResults: resultSummary(shacl.resolvedResults),
    blockingMessages: stringArray(impact.blockingMessages) ?? [],
  };
}

function displayLocalName(value: string): string {
  const separator = Math.max(value.lastIndexOf("#"), value.lastIndexOf("/"));
  return separator >= 0 && separator < value.length - 1 ? value.slice(separator + 1) : value;
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
