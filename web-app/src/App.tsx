import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { useState } from "react";
import ProjectListPage from "./workbench/ProjectListPage";
import ProjectWorkspace from "./workbench/ProjectWorkspace";

export default function App() {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: { staleTime: 10_000, refetchOnWindowFocus: false, retry: false },
    },
  }));

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<ProjectListPage />} />
          <Route path="/projects/:projectId/changes" element={<ProjectWorkspace initialModule="changes" />} />
          <Route path="/projects/:projectId" element={<ProjectWorkspace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
