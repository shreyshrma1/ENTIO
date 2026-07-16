import { useQuery } from "@tanstack/react-query";
import {
  loadEntityDetails,
  loadHierarchy,
  loadProjectSources,
  loadProjectSummary,
  loadProjects,
  searchProject,
  type WebEntityDetailResponse,
  type WebHierarchyResponse,
  type WebProjectSummaryResponse,
  type WebSemanticSearchResponse,
} from "./projectApi";
import type { WebPage } from "./contracts";

export const queryKeys = {
  projects: ["projects"] as const,
  summary: (projectId: string) => ["project", projectId, "summary"] as const,
  sources: (projectId: string) => ["project", projectId, "sources"] as const,
  hierarchy: (projectId: string, sourceId?: string, parentIri?: string) =>
    ["project", projectId, "hierarchy", sourceId ?? null, parentIri ?? null] as const,
  entity: (projectId: string, iri: string) => ["project", projectId, "entity", iri] as const,
  search: (projectId: string, text: string) => ["project", projectId, "search", text] as const,
};

export function useProjects() {
  return useQuery({ queryKey: queryKeys.projects, queryFn: () => loadProjects() });
}

export function useProjectSummary(projectId: string) {
  return useQuery<WebProjectSummaryResponse>({
    queryKey: queryKeys.summary(projectId),
    queryFn: () => loadProjectSummary(projectId),
    enabled: projectId.length > 0,
  });
}

export function useProjectSources(projectId: string) {
  return useQuery<WebPage<{ id: string; path: string; format: string; roles: string[]; tripleCount: number }>>({
    queryKey: queryKeys.sources(projectId),
    queryFn: () => loadProjectSources(projectId),
    enabled: projectId.length > 0,
  });
}

export function useHierarchy(projectId: string, sourceId?: string, parentIri?: string, enabled = true) {
  return useQuery<WebHierarchyResponse>({
    queryKey: queryKeys.hierarchy(projectId, sourceId, parentIri),
    queryFn: () => loadHierarchy(projectId, { sourceId, parentIri }),
    enabled: enabled && projectId.length > 0,
  });
}

export function useEntityDetails(projectId: string, iri: string) {
  return useQuery<WebEntityDetailResponse>({
    queryKey: queryKeys.entity(projectId, iri),
    queryFn: () => loadEntityDetails(projectId, iri),
    enabled: projectId.length > 0 && iri.length > 0,
  });
}

export function useProjectSearch(projectId: string, text: string) {
  return useQuery<WebSemanticSearchResponse>({
    queryKey: queryKeys.search(projectId, text),
    queryFn: () => searchProject(projectId, text),
    enabled: projectId.length > 0 && text.trim().length > 0,
  });
}
