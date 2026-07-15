import { randomBytes } from "node:crypto";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import * as vscode from "vscode";
import { EntioEngineClient } from "./engineCli";
import { detectEntioProject } from "./projectDetector";
import { renderWorkbench } from "./webview";
import {
  createProposalImpactModel,
  createReasoningModel,
  createSemanticDescriptorModel,
  createSemanticSearchModel,
  createShaclShapesModel,
  createShaclValidationModel,
  createWorkbenchModel,
} from "./workbenchModel";
import {
  createProposalActionResult,
  createProposalPreviewModel,
  createDeletionDependencyModel,
  createCombinedProposalModel,
  combinedPreviewAsProposalPreviewModel,
  createEntityResolutionModel,
  createGeneratedIriModel,
  deletionDependenciesInvocationArgs,
  combinedProposalInvocationArgs,
  createCombinedProposalRequest,
  entityResolutionInvocationArgs,
  generatedIriInvocationArgs,
  proposalActionInvocationArgs,
  proposalPreviewInvocationArgs,
  proposalPreviewError,
  readEntitySelectorRequest,
  readCombinedProposalRequests,
  readProposalPreviewRequest,
} from "./proposalPreview";

export function activate(context: vscode.ExtensionContext): void {
  const command = vscode.commands.registerCommand(
    "entio.openOntologyWorkbench",
    async () => {
      const panel = vscode.window.createWebviewPanel(
        "entioOntologyWorkbench",
        "Entio Ontology Workbench",
        vscode.ViewColumn.One,
        { enableScripts: true, retainContextWhenHidden: true },
      );
      const nonce = randomBytes(16).toString("hex");
      panel.webview.html = renderWorkbench(panel.webview, nonce);

      const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
      const project = detectEntioProject(workspaceRoot);
      if (!project) {
        await panel.webview.postMessage({
          type: "error",
          message: "No Entio project found in the active workspace.",
        });
        return;
      }

      const cliCommand = vscode.workspace
        .getConfiguration("entio")
        .get<string>("cliCommand", "entio");
      const engine = new EntioEngineClient(cliCommand);
      let combinedRequestFile: string | undefined;
      const writeRequestFile = async (value: unknown): Promise<string> => {
        const requests = readCombinedProposalRequests(value);
        const request = requests ? createCombinedProposalRequest(requests) : undefined;
        if (!request) throw new Error("The staged changes cannot form one combined proposal request.");
        const directory = await mkdtemp(join(tmpdir(), "entio-vscode-"));
        const file = join(directory, "proposal.json");
        await writeFile(file, JSON.stringify(request), "utf8");
        return file;
      };
      const writeCombinedRequest = async (value: unknown): Promise<string> => {
        const file = await writeRequestFile(value);
        combinedRequestFile = file;
        return file;
      };
      const removeRequestFile = async (file: string): Promise<void> => {
        await rm(file, { force: true });
        await rm(join(file, ".."), { recursive: true, force: true });
      };
      const removeCombinedRequest = async (): Promise<void> => {
        if (!combinedRequestFile) return;
        const file = combinedRequestFile;
        combinedRequestFile = undefined;
        await removeRequestFile(file);
      };
      const refresh = async (): Promise<void> => {
        try {
          const response = await engine.run(
            ["project-summary", project.rootPath],
            project.rootPath,
          );
          const model = createWorkbenchModel(response);
          if (!model) {
            throw new Error("Entio CLI returned an invalid project summary.");
          }
          await panel.webview.postMessage({ type: "project-summary", payload: model });
        } catch (error) {
          await panel.webview.postMessage({
            type: "error",
            message: error instanceof Error ? error.message : "Entio CLI invocation failed.",
          });
        }
      };
      const refreshPhase4 = async (): Promise<void> => {
        const responses = await Promise.all([
          engine.run(["reasoning-refresh", project.rootPath], project.rootPath),
          engine.run(["shacl-validate", project.rootPath, "--mode", "asserted-only"], project.rootPath),
          engine.run(["shacl-shapes", project.rootPath], project.rootPath),
        ]);
        await panel.webview.postMessage({
          type: "phase4-state",
          payload: {
            reasoning: createReasoningModel(responses[0]),
            validation: createShaclValidationModel(responses[1]),
            shapes: createShaclShapesModel(responses[2]),
          },
        });
      };
      const messageSubscription = panel.webview.onDidReceiveMessage(async (message: { type?: string }) => {
        if (message.type === "refresh") {
          await refresh();
          try {
            await refreshPhase4();
          } catch (error) {
            await panel.webview.postMessage({ type: "phase4-error", message: error instanceof Error ? error.message : "Entio Phase 4 refresh failed." });
          }
          return;
        }

        if (message.type === "phase4-refresh") {
          try {
            await refreshPhase4();
          } catch (error) {
            await panel.webview.postMessage({ type: "phase4-error", message: error instanceof Error ? error.message : "Entio Phase 4 refresh failed." });
          }
          return;
        }

        if (message.type === "semantic-describe") {
          const payload = message as { payload?: { iri?: unknown; language?: unknown } };
          const iri = typeof payload.payload?.iri === "string" ? payload.payload.iri : undefined;
          if (!iri) {
            await panel.webview.postMessage({
              type: "semantic-descriptor-error",
              message: "A symbol IRI is required to load semantic details.",
            });
            return;
          }
          try {
            const language = typeof payload.payload?.language === "string" ? payload.payload.language : undefined;
            const args = ["descriptor", project.rootPath, iri, ...(language ? ["--language", language] : [])];
            const response = await engine.run(args, project.rootPath);
            const descriptor = createSemanticDescriptorModel(response);
            if (!descriptor) throw new Error("Entio CLI returned an invalid semantic descriptor.");
            await panel.webview.postMessage({ type: "semantic-descriptor", payload: descriptor });
          } catch (error) {
            await panel.webview.postMessage({
              type: "semantic-descriptor-error",
              message: error instanceof Error ? error.message : "Entio semantic descriptor lookup failed.",
            });
          }
          return;
        }

        if (message.type === "semantic-search") {
          const payload = message as { payload?: { query?: unknown; kind?: unknown; sourceId?: unknown; language?: unknown } };
          const query = typeof payload.payload?.query === "string" ? payload.payload.query.trim() : "";
          if (!query) {
            await panel.webview.postMessage({
              type: "semantic-search-error",
              message: "Enter text to search semantic descriptions.",
            });
            return;
          }
          try {
            const args = ["search", project.rootPath, query];
            if (typeof payload.payload?.kind === "string" && payload.payload.kind) {
              args.push("--kind", payload.payload.kind);
            }
            if (typeof payload.payload?.sourceId === "string" && payload.payload.sourceId) {
              args.push("--source-id", payload.payload.sourceId);
            }
            if (typeof payload.payload?.language === "string" && payload.payload.language) {
              args.push("--language", payload.payload.language);
            }
            const response = await engine.run(args, project.rootPath);
            const search = createSemanticSearchModel(response);
            if (!search) throw new Error("Entio CLI returned an invalid semantic search result.");
            await panel.webview.postMessage({ type: "semantic-search", payload: search });
          } catch (error) {
            await panel.webview.postMessage({
              type: "semantic-search-error",
              message: error instanceof Error ? error.message : "Entio semantic search failed.",
            });
          }
          return;
        }

        if (message.type === "resolve-edit-entities") {
          const payload = message as {
            payload?: {
              sourceId?: unknown;
              entries?: unknown;
            };
          };
          const sourceId = typeof payload.payload?.sourceId === "string" ? payload.payload.sourceId : undefined;
          const entries = Array.isArray(payload.payload?.entries)
            ? payload.payload.entries.filter((entry): entry is {
              field: string;
              label: string;
              kind?: string;
              description: string;
            } => {
              if (!entry || typeof entry !== "object") return false;
              const value = entry as Record<string, unknown>;
              return typeof value.field === "string" && typeof value.label === "string" &&
                typeof value.description === "string" &&
                (value.kind === undefined || typeof value.kind === "string");
            })
            : [];
          if (!sourceId || entries.length === 0) {
            await panel.webview.postMessage({
              type: "edit-entities-error",
              message: !sourceId
                ? "A source is required to verify edit fields."
                : "At least one existing entity label is required.",
            });
            return;
          }
          try {
            const entities: Record<string, string> = {};
            for (const entry of entries) {
              const response = await engine.run(
                entityResolutionInvocationArgs(project.rootPath, {
                  label: entry.label.trim(),
                  kind: entry.kind,
                  sourceId,
                }),
                project.rootPath,
              );
              const resolution = createEntityResolutionModel(response);
              if (resolution?.status === "not-found") {
                throw new Error("The " + entry.description + " '" + entry.label + "' does not exist in the selected source.");
              }
              if (resolution?.status !== "resolved" || !resolution.candidate) {
                throw new Error("The " + entry.description + " '" + entry.label + "' could not be uniquely verified in the selected source.");
              }
              entities[entry.field] = resolution.candidate.iri;
            }
            await panel.webview.postMessage({
              type: "edit-entities-resolved",
              payload: { entities },
            });
          } catch (error) {
            await panel.webview.postMessage({
              type: "edit-entities-error",
              message: error instanceof Error ? error.message : "Entio could not verify the edit fields.",
            });
          }
          return;
        }

        if (message.type === "semantic-preview") {
          const request = readProposalPreviewRequest((message as { payload?: unknown }).payload);
          if (!request || !request.editKind.startsWith("create-annotation") && ![
            "add-definition", "replace-definition", "remove-definition",
            "add-alternate-label", "replace-alternate-label", "remove-alternate-label",
            "add-annotation", "remove-annotation",
          ].includes(request.editKind)) {
            await panel.webview.postMessage({
              type: "proposal-preview-error",
              message: "The semantic edit preview request is invalid.",
            });
            return;
          }
          try {
            const file = await writeCombinedRequest([request]);
            const response = await engine.run(
              combinedProposalInvocationArgs(project.rootPath, file, "preview"),
              project.rootPath,
            );
            const combined = createCombinedProposalModel(response);
            if (!combined) throw new Error("Entio CLI returned an invalid semantic edit preview.");
            await panel.webview.postMessage({
              type: "proposal-preview",
              payload: combinedPreviewAsProposalPreviewModel(combined, request),
            });
          } catch (error) {
            await panel.webview.postMessage({
              type: "proposal-preview-error",
              message: error instanceof Error ? error.message : "Entio semantic edit preview failed.",
            });
          } finally {
            await removeCombinedRequest();
          }
          return;
        }

        if (message.type === "proposal-preview") {
          const request = readProposalPreviewRequest((message as { payload?: unknown }).payload);
          if (!request) {
            await panel.webview.postMessage({
              type: "proposal-preview-error",
              message: "The proposal preview request is invalid.",
            });
            return;
          }

          try {
            const response = await engine.run(
              proposalPreviewInvocationArgs(project.rootPath, request),
              project.rootPath,
            );
            const preview = response.ok ? createProposalPreviewModel(response) : undefined;
            if (response.ok && !preview) {
              throw new Error("Entio CLI returned an invalid proposal preview.");
            }
            await panel.webview.postMessage({
              type: response.ok ? "proposal-preview" : "proposal-preview-error",
              payload: preview,
              message: response.ok ? undefined : proposalPreviewError(response),
            });
          } catch (error) {
            await panel.webview.postMessage({
              type: "proposal-preview-error",
              message: error instanceof Error ? error.message : "Entio preview invocation failed.",
            });
          }
        }

        if (message.type === "proposal-action") {
          const actionMessage = message as { action?: unknown; payload?: unknown };
          const action = actionMessage.action === "apply" || actionMessage.action === "reject"
            ? actionMessage.action
            : undefined;
          const request = readProposalPreviewRequest(actionMessage.payload);
          if (!action || !request) {
            await panel.webview.postMessage({
              type: "proposal-action-error",
              message: "The proposal action request is invalid.",
            });
            return;
          }

          try {
            const response = await engine.run(
              proposalActionInvocationArgs(action, project.rootPath, request),
              project.rootPath,
            );
            const result = createProposalActionResult(action, response);
            if (!result) {
              throw new Error("Entio CLI returned an invalid proposal action result.");
            }
            await panel.webview.postMessage({ type: "proposal-action-result", payload: result });
            if (action === "apply" && result.ok && result.status === "applied") {
              await refresh();
            }
          } catch (error) {
            await panel.webview.postMessage({
              type: "proposal-action-error",
              message: error instanceof Error ? error.message : "Entio proposal action failed.",
            });
          }
        }

        if (message.type === "combined-preview") {
          try {
            const payload = (message as { payload?: unknown }).payload;
            const file = await writeCombinedRequest(payload);
            const response = await engine.run(
              combinedProposalInvocationArgs(project.rootPath, file, "preview"),
              project.rootPath,
            );
            const combined = createCombinedProposalModel(response);
            if (!combined) throw new Error("Entio CLI returned an invalid combined proposal preview.");
            await panel.webview.postMessage({ type: "combined-preview", payload: combined });
            const impactFile = await writeRequestFile(payload);
            try {
              const impactResponse = await engine.run(["proposal-impact", project.rootPath, "--request-file", impactFile], project.rootPath);
              const impact = createProposalImpactModel(impactResponse);
              if (impact) await panel.webview.postMessage({ type: "proposal-impact", payload: impact });
            } finally {
              await removeRequestFile(impactFile);
            }
          } catch (error) {
            await panel.webview.postMessage({
              type: "combined-preview-error",
              message: error instanceof Error ? error.message : "Entio combined preview invocation failed.",
            });
          }
        }

        if (message.type === "deletion-preview") {
          const request = readProposalPreviewRequest((message as { payload?: unknown }).payload);
          if (!request || request.editKind !== "delete-entity") {
            await panel.webview.postMessage({
              type: "deletion-preview-error",
              message: "The deletion preview request is invalid.",
            });
            return;
          }
          try {
            const file = await writeCombinedRequest([request]);
            const response = await engine.run(
              combinedProposalInvocationArgs(project.rootPath, file, "preview"),
              project.rootPath,
            );
            const combined = createCombinedProposalModel(response);
            if (!combined) throw new Error("Entio CLI returned an invalid deletion proposal preview.");
            await panel.webview.postMessage({ type: "deletion-preview", payload: combined });
          } catch (error) {
            await panel.webview.postMessage({
              type: "deletion-preview-error",
              message: error instanceof Error ? error.message : "Entio deletion preview invocation failed.",
            });
          } finally {
            await removeCombinedRequest();
          }
        }

        if (message.type === "combined-action") {
          const action = (message as { action?: unknown }).action;
          if ((action !== "apply" && action !== "reject") || !combinedRequestFile) {
            await panel.webview.postMessage({
              type: "combined-action-error",
              action: typeof action === "string" ? action : "apply",
              message: "No combined proposal is ready for this action.",
            });
            return;
          }
          try {
            const response = await engine.run(
              combinedProposalInvocationArgs(project.rootPath, combinedRequestFile, action),
              project.rootPath,
            );
            const combined = createCombinedProposalModel(response);
            if (!combined) throw new Error("Entio CLI returned an invalid combined proposal action result.");
            await panel.webview.postMessage({ type: "combined-action-result", payload: combined });
            if (action === "apply" && combined.ok && combined.status === "applied") {
              await removeCombinedRequest();
              await refresh();
            } else if (action === "reject" && combined.ok) {
              await removeCombinedRequest();
            }
          } catch (error) {
            await panel.webview.postMessage({
              type: "combined-action-error",
              action,
              message: error instanceof Error ? error.message : "Entio combined proposal action failed.",
            });
          }
        }

        if (message.type === "generate-iri") {
          const payload = message as { payload?: { label?: unknown; kind?: unknown; distinct?: unknown } };
          const label = typeof payload.payload?.label === "string" ? payload.payload.label : undefined;
          const kind = typeof payload.payload?.kind === "string" ? payload.payload.kind : undefined;
          if (!label || !kind) {
            await panel.webview.postMessage({
              type: "generated-iri-error",
              message: "A label and entity kind are required to generate an IRI.",
            });
            return;
          }
          try {
            const response = await engine.run(
              generatedIriInvocationArgs(project.rootPath, label, kind, payload.payload?.distinct === true),
              project.rootPath,
            );
            const generated = createGeneratedIriModel(response);
            if (!generated) throw new Error("Entio CLI returned an invalid generated IRI result.");
            await panel.webview.postMessage({ type: "generated-iri", payload: generated });
          } catch (error) {
            await panel.webview.postMessage({
              type: "generated-iri-error",
              message: error instanceof Error ? error.message : "Entio IRI generation failed.",
            });
          }
        }

        if (message.type === "deletion-dependencies") {
          const payload = message as { payload?: { sourceId?: unknown } & Record<string, unknown> };
          const selector = readEntitySelectorRequest(payload.payload);
          const sourceId = typeof payload.payload?.sourceId === "string" ? payload.payload.sourceId : undefined;
          if (!selector || !sourceId) {
            await panel.webview.postMessage({
              type: "deletion-dependencies-error",
              message: "A deletion target and source are required.",
            });
            return;
          }
          try {
            const response = await engine.run(
              deletionDependenciesInvocationArgs(project.rootPath, sourceId, selector),
              project.rootPath,
            );
            const dependencies = createDeletionDependencyModel(response);
            if (!dependencies) throw new Error("Entio CLI returned an invalid deletion dependency result.");
            await panel.webview.postMessage({ type: "deletion-dependencies", payload: dependencies });
          } catch (error) {
            await panel.webview.postMessage({
              type: "deletion-dependencies-error",
              message: error instanceof Error ? error.message : "Entio deletion analysis failed.",
            });
          }
        }

        if (message.type === "open-source") {
          const sourceMessage = message as { path?: unknown };
          if (typeof sourceMessage.path !== "string") {
            return;
          }

          try {
            const document = await vscode.workspace.openTextDocument(sourceMessage.path);
            await vscode.window.showTextDocument(document);
          } catch (error) {
            await vscode.window.showErrorMessage(
              error instanceof Error ? error.message : "The changed ontology source could not be opened.",
            );
          }
        }
      });
      const watcher = vscode.workspace.createFileSystemWatcher(
        new vscode.RelativePattern(project.rootPath, "**/*.ttl"),
      );
      const watcherSubscriptions = [
        watcher.onDidChange(refresh),
        watcher.onDidCreate(refresh),
        watcher.onDidDelete(refresh),
      ];
      panel.onDidDispose(() => {
        messageSubscription.dispose();
        watcherSubscriptions.forEach((subscription) => subscription.dispose());
        watcher.dispose();
        void removeCombinedRequest();
      });
      await refresh();
      try {
        await refreshPhase4();
      } catch (error) {
        await panel.webview.postMessage({ type: "phase4-error", message: error instanceof Error ? error.message : "Entio Phase 4 refresh failed." });
      }
    },
  );

  context.subscriptions.push(command);
}

export function deactivate(): void {}
