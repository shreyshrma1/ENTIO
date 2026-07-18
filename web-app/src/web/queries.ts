import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  loadEntityDetails,
  loadHierarchy,
  loadProjectOutline,
  loadProjectSources,
  loadProjectSummary,
  loadProjects,
  searchProject,
  loadStagedChanges,
  stageChange,
  discardStagedChange,
  previewStagedChanges,
  approveProposal,
  rejectProposal,
  applyProposal,
  cancelSemanticJob,
  loadSemanticJob,
  submitSemanticJob,
  type WebSemanticJobRequest,
  type WebSemanticJobStatus,
  loadFiboDetails,
  loadFiboModuleElements,
  loadFiboModules,
  searchFibo,
  stageFiboProposal,
  type WebFiboDetailsResponse,
  type WebFiboElement,
  type WebFiboModule,
  type WebFiboProposalRequest,
  loadAiCredentialStatus,
  removeAiCredential,
  saveAiCredential,
  testAiCredential,
  type WebAiCredentialStatus,
  type WebAiCredentialTestResponse,
  askAiAssistant,
  analyzeAiDraft,
  cancelAiRun,
  createAiConversation,
  deleteAiConversation,
  loadAiConversation,
  loadAiConversations,
  loadAiDraft,
  sendAiConversationMessage,
  submitAiDraftForReview,
  type WebAiAssistantRequest,
  type WebAiAssistantResponse,
  type WebEntityDetailResponse,
  type WebHierarchyResponse,
  type WebOutlineResponse,
  type WebProjectSummaryResponse,
  type WebSemanticSearchResponse,
} from "./projectApi";
import type {
  WebAiConversationListResponse,
  WebAiConversationResponse,
  WebAiConversationTurnResponse,
  WebAiDraftAnalysisResponse,
  WebAiDraftResponse,
  WebAiMessageRequest,
  WebAiReviewSubmissionRequest,
  WebAiReviewSubmissionResponse,
  WebAiRunResponse,
  WebPage,
} from "./contracts";

export const queryKeys = {
  projects: ["projects"] as const,
  summary: (projectId: string) => ["project", projectId, "summary"] as const,
  sources: (projectId: string) => ["project", projectId, "sources"] as const,
  hierarchy: (projectId: string, sourceId?: string, parentIri?: string) =>
    ["project", projectId, "hierarchy", sourceId ?? null, parentIri ?? null] as const,
  outline: (projectId: string, sourceId?: string) =>
    ["project", projectId, "outline", sourceId ?? null] as const,
  entity: (projectId: string, iri: string) => ["project", projectId, "entity", iri] as const,
  search: (projectId: string, text: string) => ["project", projectId, "search", text] as const,
  staged: (projectId: string) => ["project", projectId, "staged"] as const,
  semanticJob: (projectId: string, jobId: string) => ["project", projectId, "semantic-job", jobId] as const,
  fiboModules: (projectId: string) => ["project", projectId, "fibo", "modules"] as const,
  fiboElements: (projectId: string, moduleIri: string) => ["project", projectId, "fibo", "elements", moduleIri] as const,
  fiboSearch: (projectId: string, text: string) => ["project", projectId, "fibo", "search", text] as const,
  fiboDetails: (projectId: string, iri: string) => ["project", projectId, "fibo", "details", iri] as const,
  aiCredentialStatus: ["ai", "credential-status"] as const,
  aiAssistant: (projectId: string) => ["project", projectId, "ai", "assistant"] as const,
  aiConversations: (projectId: string) => ["project", projectId, "ai", "conversations"] as const,
  aiConversation: (projectId: string, conversationId: string) => ["project", projectId, "ai", "conversation", conversationId] as const,
  aiDraft: (projectId: string, draftId: string) => ["project", projectId, "ai", "draft", draftId] as const,
  aiAnalysis: (projectId: string, draftId: string) => ["project", projectId, "ai", "analysis", draftId] as const,
  aiRun: (projectId: string, runId: string) => ["project", projectId, "ai", "run", runId] as const,
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

export function useProjectOutline(projectId: string, sourceId?: string) {
  return useQuery<WebOutlineResponse>({
    queryKey: queryKeys.outline(projectId, sourceId),
    queryFn: () => loadProjectOutline(projectId, { sourceId, limit: 100 }),
    enabled: projectId.length > 0,
  });
}

export function useEntityDetails(projectId: string, iri: string, enabled = true) {
  return useQuery<WebEntityDetailResponse>({
    queryKey: queryKeys.entity(projectId, iri),
    queryFn: () => loadEntityDetails(projectId, iri),
    enabled: enabled && projectId.length > 0 && iri.length > 0,
  });
}

export function useProjectSearch(projectId: string, text: string) {
  return useQuery<WebSemanticSearchResponse>({
    queryKey: queryKeys.search(projectId, text),
    queryFn: () => searchProject(projectId, text),
    enabled: projectId.length > 0 && text.trim().length > 0,
  });
}

export function useStagedChanges(projectId: string) {
  return useQuery({ queryKey: queryKeys.staged(projectId), queryFn: () => loadStagedChanges(projectId), enabled: projectId.length > 0 });
}

export function useStagingActions(projectId: string) {
  const queryClient = useQueryClient();
  const refresh = (data: Awaited<ReturnType<typeof loadStagedChanges>>) => {
    queryClient.setQueryData(queryKeys.staged(projectId), data);
    void queryClient.invalidateQueries({ queryKey: queryKeys.summary(projectId) });
  };
  const refreshApplied = async (data: Awaited<ReturnType<typeof loadStagedChanges>>) => {
    queryClient.setQueryData(queryKeys.staged(projectId), data);
    await queryClient.invalidateQueries({ queryKey: ["project", projectId] });
  };
  return {
    stage: useMutation({ mutationFn: (request: Parameters<typeof stageChange>[1]) => stageChange(projectId, request), onSuccess: refresh }),
    discard: useMutation({ mutationFn: (id: string) => discardStagedChange(projectId, id), onSuccess: refresh }),
    preview: useMutation({ mutationFn: () => previewStagedChanges(projectId), onSuccess: refresh }),
    approve: useMutation({ mutationFn: () => approveProposal(projectId), onSuccess: refresh }),
    reject: useMutation({ mutationFn: () => rejectProposal(projectId), onSuccess: refresh }),
    apply: useMutation({ mutationFn: () => applyProposal(projectId), onSuccess: refreshApplied }),
    accept: useMutation({
      mutationFn: async () => {
        const current = queryClient.getQueryData<Awaited<ReturnType<typeof loadStagedChanges>>>(queryKeys.staged(projectId));
        if (current?.proposal?.status !== "APPROVED") {
          const approved = await approveProposal(projectId);
          queryClient.setQueryData(queryKeys.staged(projectId), approved);
        }
        return applyProposal(projectId);
      },
      onSuccess: refreshApplied,
    }),
  };
}

export function useSemanticJob(projectId: string, jobId: string | null) {
  return useQuery<WebSemanticJobStatus>({
    queryKey: queryKeys.semanticJob(projectId, jobId ?? ""),
    queryFn: () => loadSemanticJob(projectId, jobId!),
    enabled: projectId.length > 0 && Boolean(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ["Completed", "Failed", "Cancelled", "Incomplete", "Stale"].includes(status) ? false : 750;
    },
  });
}

export function useSemanticJobActions(projectId: string) {
  const queryClient = useQueryClient();
  const refresh = (status: WebSemanticJobStatus) => {
    queryClient.setQueryData(queryKeys.semanticJob(projectId, status.id), status);
  };
  return {
    submit: useMutation({ mutationFn: (request: WebSemanticJobRequest) => submitSemanticJob(projectId, request), onSuccess: refresh }),
    cancel: useMutation({ mutationFn: (jobId: string) => cancelSemanticJob(projectId, jobId), onSuccess: refresh }),
  };
}

export function useFiboModules(projectId: string) {
  return useQuery<{ sourceId: string; release: string; page: WebPage<WebFiboModule> }>({
    queryKey: queryKeys.fiboModules(projectId),
    queryFn: () => loadFiboModules(projectId),
    enabled: projectId.length > 0,
  });
}

export function useFiboModuleElements(projectId: string, moduleIri: string | null) {
  return useQuery<{ moduleIri: string; page: WebPage<WebFiboElement> }>({
    queryKey: queryKeys.fiboElements(projectId, moduleIri ?? ""),
    queryFn: () => loadFiboModuleElements(projectId, moduleIri!),
    enabled: projectId.length > 0 && Boolean(moduleIri),
  });
}

export function useFiboSearch(projectId: string, text: string) {
  return useQuery<{ query: string; page: WebPage<WebFiboElement> }>({
    queryKey: queryKeys.fiboSearch(projectId, text),
    queryFn: () => searchFibo(projectId, text),
    enabled: projectId.length > 0 && text.trim().length > 0,
  });
}

export function useFiboDetails(projectId: string, iri: string | null) {
  return useQuery<WebFiboDetailsResponse>({
    queryKey: queryKeys.fiboDetails(projectId, iri ?? ""),
    queryFn: () => loadFiboDetails(projectId, iri!),
    enabled: projectId.length > 0 && Boolean(iri),
  });
}

export function useFiboActions(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: WebFiboProposalRequest) => stageFiboProposal(projectId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) }),
  });
}

export function useAiCredentialStatus() {
  return useQuery<WebAiCredentialStatus>({
    queryKey: queryKeys.aiCredentialStatus,
    queryFn: () => loadAiCredentialStatus(),
  });
}

export function useAiCredentialActions() {
  const queryClient = useQueryClient();
  const refresh = (status: WebAiCredentialStatus) => queryClient.setQueryData(queryKeys.aiCredentialStatus, status);
  return {
    save: useMutation({ mutationFn: ({ providerId, apiKey }: { providerId: string; apiKey: string }) => saveAiCredential(providerId, apiKey), onSuccess: refresh }),
    test: useMutation<WebAiCredentialTestResponse, Error, void>({ mutationFn: () => testAiCredential() }),
    remove: useMutation({ mutationFn: () => removeAiCredential(), onSuccess: refresh }),
  };
}

export function useAiAssistant(projectId: string) {
  return useMutation<WebAiAssistantResponse, Error, WebAiAssistantRequest>({
    mutationFn: (request) => askAiAssistant(projectId, request),
  });
}

export function useAiConversations(projectId: string) {
  return useQuery<WebAiConversationListResponse>({
    queryKey: queryKeys.aiConversations(projectId),
    queryFn: () => loadAiConversations(projectId),
    enabled: projectId.length > 0,
  });
}

export function useAiConversation(projectId: string, conversationId: string | null) {
  return useQuery<WebAiConversationResponse>({
    queryKey: queryKeys.aiConversation(projectId, conversationId ?? ""),
    queryFn: () => loadAiConversation(projectId, conversationId!),
    enabled: projectId.length > 0 && Boolean(conversationId),
  });
}

export function useAiDraft(projectId: string, draftId: string | null) {
  return useQuery<WebAiDraftResponse>({
    queryKey: queryKeys.aiDraft(projectId, draftId ?? ""),
    queryFn: () => loadAiDraft(projectId, draftId!),
    enabled: projectId.length > 0 && Boolean(draftId),
  });
}

export function useAiConversationActions(projectId: string) {
  const queryClient = useQueryClient();
  const cacheConversation = (response: WebAiConversationResponse) => {
    queryClient.setQueryData(queryKeys.aiConversation(projectId, response.conversation.id), response);
    queryClient.setQueryData<WebAiConversationListResponse>(queryKeys.aiConversations(projectId), (current) => {
      const conversations = current?.conversations.filter((item) => item.id !== response.conversation.id) ?? [];
      return { apiVersion: "v1", conversations: [response.conversation, ...conversations] };
    });
  };
  return {
    create: useMutation<WebAiConversationResponse, Error, void>({
      mutationFn: () => createAiConversation(projectId),
      onSuccess: cacheConversation,
    }),
    send: useMutation<WebAiConversationTurnResponse, Error, { conversationId: string; request: WebAiMessageRequest; idempotencyKey: string }>({
      mutationFn: ({ conversationId, request, idempotencyKey }) => sendAiConversationMessage(projectId, conversationId, request, idempotencyKey),
      onSuccess: (response) => cacheConversation({ apiVersion: "v1", conversation: response.conversation }),
    }),
    cancel: useMutation<WebAiRunResponse, Error, string>({
      mutationFn: (runId) => cancelAiRun(projectId, runId),
      onSuccess: (response) => queryClient.setQueryData(queryKeys.aiRun(projectId, response.run.id), response),
    }),
    remove: useMutation<{ apiVersion: "v1"; status: string }, Error, string>({
      mutationFn: (conversationId) => deleteAiConversation(projectId, conversationId),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.aiConversations(projectId) }),
    }),
  };
}

export function useAiDraftActions(projectId: string) {
  const queryClient = useQueryClient();
  return {
    analyze: useMutation<WebAiDraftAnalysisResponse, Error, string>({
      mutationFn: (draftId) => analyzeAiDraft(projectId, draftId),
      onSuccess: (response) => {
        queryClient.setQueryData(queryKeys.aiAnalysis(projectId, response.analysis.draftId), response);
        queryClient.invalidateQueries({ queryKey: queryKeys.aiDraft(projectId, response.analysis.draftId) });
      },
    }),
    submit: useMutation<WebAiReviewSubmissionResponse, Error, { draftId: string; request: WebAiReviewSubmissionRequest; idempotencyKey: string }>({
      mutationFn: ({ draftId, request, idempotencyKey }) => submitAiDraftForReview(projectId, draftId, request, idempotencyKey),
      onSuccess: (_response, variables) => {
        queryClient.invalidateQueries({ queryKey: queryKeys.aiDraft(projectId, variables.draftId) });
        queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) });
      },
    }),
  };
}
