import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { queryKeys, useAiTask, useAiTaskActions, useAiTaskWorkspace } from "../../web/queries";
import { streamAiTaskEvents } from "../../web/projectApi";
import type { WebAiTaskEvent } from "../../web/contracts";

export default function AiTaskWorkspace({ projectId, taskId }: { projectId: string; taskId: string }) {
  const queryClient = useQueryClient();
  const taskQuery = useAiTask(projectId, taskId);
  const workspaceQuery = useAiTaskWorkspace(projectId, taskId);
  const actions = useAiTaskActions(projectId);
  const [events, setEvents] = useState<WebAiTaskEvent[]>([]);
  const [streamState, setStreamState] = useState("Connecting");
  const lastEventId = useRef<string | undefined>(undefined);

  useEffect(() => {
    const controller = new AbortController();
    streamAiTaskEvents(projectId, taskId, {
      signal: controller.signal,
      lastEventId: lastEventId.current,
      onEvent: (event, eventId) => {
        if (eventId) lastEventId.current = eventId;
        setStreamState("Connected");
        setEvents((current) => [...current.filter((item) => item.sequence !== event.sequence), event].sort((a, b) => a.sequence - b.sequence));
      },
      onResynchronization: () => {
        lastEventId.current = undefined;
        setStreamState("Resynchronizing");
        void queryClient.invalidateQueries({ queryKey: queryKeys.aiTask(projectId, taskId) });
        void queryClient.invalidateQueries({ queryKey: queryKeys.aiTaskWorkspace(projectId, taskId) });
      },
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setStreamState(error instanceof Error ? error.message : "Disconnected");
    });
    return () => controller.abort();
  }, [projectId, queryClient, taskId]);

  const task = taskQuery.data?.task ?? workspaceQuery.data?.workspace.task;
  const workspace = workspaceQuery.data?.workspace;
  if (taskQuery.isPending || workspaceQuery.isPending) return <section className="ai-task-workspace" aria-live="polite">Loading task workspace…</section>;
  if (!task || !workspace || taskQuery.isError || workspaceQuery.isError) return <section className="ai-task-workspace" role="alert">Task workspace unavailable. Non-AI workbench features remain available.</section>;

  const terminal = ["SUBMITTED_FOR_REVIEW", "FAILED", "CANCELLED"].includes(task.status);
  const canPause = !terminal && !["PAUSED", "STALE", "LIMIT_REACHED"].includes(task.status);
  const canResume = task.status === "PAUSED";
  const canSubmit = task.status === "READY_FOR_REVIEW";
  const command = (action: "execute" | "pause" | "resume" | "cancel" | "submit") => actions.command.mutate({
    taskId,
    action,
    request: { expectedRevision: task.revision },
    idempotencyKey: taskRequestId(action),
  });

  return <section className="ai-task-workspace" aria-labelledby="ai-task-heading">
    <header className="ai-task-header">
      <div><p className="eyebrow">Task workspace</p><h3 id="ai-task-heading">{task.objective}</h3></div>
      <span className="ai-state">{task.status.replaceAll("_", " ")}</span>
    </header>
    <dl className="ai-task-facts">
      <div><dt>Model</dt><dd>{task.modelId}</dd></div><div><dt>Project</dt><dd>{task.projectId}</dd></div>
      <div><dt>Current package</dt><dd>{task.currentWorkPackageId ?? "None"}</dd></div>
      <div><dt>Progress</dt><dd>{task.completedWorkPackageIds.length} packages complete · {task.failedWorkPackageIds.length} blocked</dd></div>
    </dl>
    <div className="button-row" aria-label="Task controls">
      <button className="button" type="button" onClick={() => command("pause")} disabled={!canPause || actions.command.isPending} title={canPause ? "Pause after the current safe boundary" : "This task cannot be paused now"}>Pause</button>
      <button className="button" type="button" onClick={() => command("resume")} disabled={!canResume || actions.command.isPending} title={canResume ? "Resume this task" : "Only paused tasks can resume"}>Resume</button>
      <button className="button danger" type="button" onClick={() => command("cancel")} disabled={terminal || actions.command.isPending}>Cancel</button>
    </div>
    {actions.command.isError ? <p role="alert">Task action failed. {actions.command.error.message}</p> : null}
    {workspace.pauseCode ? <p className="ai-task-notice" role="status">Paused: {workspace.pauseCode}</p> : null}
    {workspace.limits.length ? <section><h4>Limits reached</h4><ul>{workspace.limits.map((limit) => <li key={limit.kind}>{limit.kind}: {limit.observed} of {limit.maximum}</li>)}</ul></section> : null}
    <section><h4>Plan and decisions</h4><p>{workspace.planId ? `Plan ${workspace.planId}, revision ${workspace.planRevision}` : "No material plan checkpoint is currently required."}</p>
      {workspace.assumptions.length ? <ul>{workspace.assumptions.map((value) => <li key={value}>{value}</li>)}</ul> : null}
      {workspace.openQuestions.length ? <div role="status"><strong>Clarification needed</strong><ul>{workspace.openQuestions.map((value) => <li key={value}>{value}</li>)}</ul></div> : null}
    </section>
    <section><h4>Draft and analysis</h4><p>{task.privateDraftId ? `Private draft ${task.privateDraftId}` : "No private draft yet."}</p><p>{workspace.analysisReferenceIds.length} deterministic analysis references · {workspace.repairCycleCount} repair cycles</p></section>
    <section aria-live="polite"><h4>Task activity</h4><p>{streamState}</p>{events.length ? <ol className="ai-task-events">{events.map((event) => <li key={event.sequence}><strong>{event.type.replaceAll("_", " ")}</strong> {event.message}</li>)}</ol> : <p>Waiting for authoritative task events.</p>}</section>
    {canSubmit ? <section className="ai-task-review"><h4>Ready for human review</h4><p>The current review package is complete. Submission stages the exact typed draft; it does not approve or apply it.</p><button className="button primary" type="button" onClick={() => command("submit")} disabled={actions.command.isPending}>Submit for human review</button></section> : null}
    {task.status === "SUBMITTED_FOR_REVIEW" ? <p className="ai-task-notice" role="status">Submitted to the ordinary proposal review queue. A human reviewer retains approval authority.</p> : null}
  </section>;
}

function taskRequestId(prefix: string): string {
  return `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
}
