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
  const symbols = Array.isArray(response.symbols)
    ? response.symbols.map(readSymbol).filter(isDefined)
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

function readSymbol(value: unknown): SymbolSummary | undefined {
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
