import type { EngineResponse } from "./engineCli";

export interface ExternalManifestModel {
  readonly sourceId: string;
  readonly release: string;
  readonly commitSha: string;
  readonly catalogSchema: string;
  readonly elementCount: number;
  readonly moduleCount: number;
  readonly availability: string;
}

export interface ExternalBrowseModel {
  readonly mode: string;
  readonly items: readonly Record<string, unknown>[];
  readonly totalCount: number;
  readonly page: number;
  readonly pageSize: number;
  readonly hasNext: boolean;
}

export interface ExternalSearchModel {
  readonly query: string;
  readonly totalResultCount: number;
  readonly page: number;
  readonly pageSize: number;
  readonly hasNext: boolean;
  readonly candidates: readonly Record<string, unknown>[];
}

export interface ExternalDescriptorModel {
  readonly kind: string;
  readonly descriptor: Record<string, unknown>;
}

export interface ExternalDependencyModel {
  readonly category: string;
  readonly selection: string;
  readonly requirement: string;
  readonly reason: string;
  readonly externalIri?: string;
  readonly sourceModule?: string;
}

export interface ExternalDependenciesModel {
  readonly requiresExplicitApproval: boolean;
  readonly dependencies: readonly ExternalDependencyModel[];
}

export interface ExternalProposalModel {
  readonly proposalId: string;
  readonly targetSourceId: string;
  readonly changeCount: number;
  readonly previewTripleCount: number;
  readonly dependencyStatus: string;
}

type RecordValue = Record<string, unknown>;

function record(value: unknown): RecordValue | undefined {
  return value && typeof value === "object" && !Array.isArray(value) ? value as RecordValue : undefined;
}

function stringValue(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function pageModel(response: unknown): ExternalBrowseModel | undefined {
  const root = record(response);
  const page = root ? record(root.page) : undefined;
  if (!root || !page || !Array.isArray(page.items) ||
      typeof page.totalCount !== "number" || typeof page.page !== "number" ||
      typeof page.pageSize !== "number" || typeof page.hasNext !== "boolean") return undefined;
  const items = page.items.map(record);
  if (items.some((item) => !item)) return undefined;
  return {
    mode: stringValue(root.mode) || "module",
    items: items as RecordValue[],
    totalCount: page.totalCount,
    page: page.page,
    pageSize: page.pageSize,
    hasNext: page.hasNext,
  };
}

export function createExternalManifestModel(response: EngineResponse): ExternalManifestModel | undefined {
  const source = record(response.source);
  const manifest = record(response.manifest);
  const catalog = record(response.catalog);
  const pack = record(response.package);
  if (!response.ok || !source || !manifest || !catalog || !pack ||
      typeof source.id !== "string" || typeof manifest.release !== "string" ||
      typeof manifest.commitSha !== "string" || typeof manifest.catalogSchema !== "string" ||
      typeof catalog.elementCount !== "number" || typeof catalog.moduleCount !== "number" ||
      typeof pack.availability !== "string") return undefined;
  return {
    sourceId: source.id,
    release: manifest.release,
    commitSha: manifest.commitSha,
    catalogSchema: manifest.catalogSchema,
    elementCount: catalog.elementCount,
    moduleCount: catalog.moduleCount,
    availability: pack.availability,
  };
}

export function createExternalBrowseModel(response: EngineResponse): ExternalBrowseModel | undefined {
  if (!response.ok) return undefined;
  return pageModel(response);
}

export function createExternalDescriptorModel(response: EngineResponse): ExternalDescriptorModel | undefined {
  const element = record(response.element);
  const descriptor = element ? record(element.descriptor) : undefined;
  if (!response.ok || !element || !descriptor || typeof element.kind !== "string") return undefined;
  return { kind: element.kind, descriptor };
}

export function createExternalSearchModel(response: EngineResponse): ExternalSearchModel | undefined {
  if (!response.ok || typeof response.query !== "string" ||
      typeof response.totalResultCount !== "number" || typeof response.page !== "number" ||
      typeof response.pageSize !== "number" || typeof response.hasNext !== "boolean" ||
      !Array.isArray(response.candidates)) return undefined;
  const candidates = response.candidates.map(record);
  if (candidates.some((candidate) => !candidate)) return undefined;
  return {
    query: response.query,
    totalResultCount: response.totalResultCount,
    page: response.page,
    pageSize: response.pageSize,
    hasNext: response.hasNext,
    candidates: candidates as RecordValue[],
  };
}

export function createExternalDependenciesModel(response: EngineResponse): ExternalDependenciesModel | undefined {
  const dependencySet = record(response.dependencySet);
  if (!dependencySet || typeof response.requiresExplicitApproval !== "boolean" || !Array.isArray(dependencySet.dependencies)) return undefined;
  const dependencies = dependencySet.dependencies.map((value): ExternalDependencyModel | undefined => {
    const dependency = record(value);
    if (!dependency || typeof dependency.category !== "string" || typeof dependency.selection !== "string" ||
        typeof dependency.requirement !== "string" || typeof dependency.reason !== "string") return undefined;
    return {
      category: dependency.category,
      selection: dependency.selection,
      requirement: dependency.requirement,
      reason: dependency.reason,
      externalIri: stringValue(dependency.externalIri),
      sourceModule: stringValue(dependency.sourceModule),
    };
  });
  if (dependencies.some((dependency) => !dependency)) return undefined;
  return { requiresExplicitApproval: response.requiresExplicitApproval, dependencies: dependencies as ExternalDependencyModel[] };
}

export function createExternalProposalModel(response: EngineResponse): ExternalProposalModel | undefined {
  const proposal = record(response.proposal);
  const preview = proposal ? record(proposal.preview) : undefined;
  const dependencies = record(response.dependencySet);
  if (!response.ok || !proposal || !preview || !dependencies ||
      typeof proposal.id !== "string" || typeof proposal.targetSourceId !== "string" ||
      typeof proposal.changeCount !== "number" || typeof preview.tripleCount !== "number" ||
      typeof dependencies.status !== "string") return undefined;
  return {
    proposalId: proposal.id,
    targetSourceId: proposal.targetSourceId,
    changeCount: proposal.changeCount,
    previewTripleCount: preview.tripleCount,
    dependencyStatus: dependencies.status,
  };
}

export function externalDescriptorLabel(value: Record<string, unknown>): string {
  const descriptor = record(value.descriptor);
  const semantic = descriptor ? record(descriptor.semantic) : undefined;
  const preferred = semantic ? record(semantic.preferredLabel) : undefined;
  return stringValue(preferred?.value) || stringValue(semantic?.iri) || "Unnamed external element";
}

export function externalDescriptorIri(value: Record<string, unknown>): string | undefined {
  const descriptor = record(value.descriptor);
  const semantic = descriptor ? record(descriptor.semantic) : undefined;
  return stringValue(semantic?.iri);
}

export function externalCandidateLabel(value: Record<string, unknown>): string {
  const element = record(value.element);
  return element ? externalDescriptorLabel(element) : "Unnamed external element";
}

export function externalCandidateIri(value: Record<string, unknown>): string | undefined {
  const element = record(value.element);
  return element ? externalDescriptorIri(element) : undefined;
}

export function externalCandidateKind(value: Record<string, unknown>): string {
  const element = record(value.element);
  return stringValue(element?.kind) || "Unknown";
}

export function externalDependencyKey(value: ExternalDependencyModel): string {
  return [value.category, value.externalIri || "", value.sourceModule || ""].join("|");
}
